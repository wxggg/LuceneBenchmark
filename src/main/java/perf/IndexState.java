package perf;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.document.IntPoint;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PointValues.IntersectVisitor;
import org.apache.lucene.index.PointValues.Relation;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.spell.DirectSpellChecker;
import org.apache.lucene.search.vectorhighlight.FastVectorHighlighter;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.bkd.BKDReader.IntersectState;
import org.apache.lucene.util.bkd.BKDReader;

class IndexState {
  public final ReferenceManager<IndexSearcher> mgr;
  public final DirectSpellChecker spellChecker;
  public final Query groupEndQuery;
  public final FastVectorHighlighter fastHighlighter;
  public final boolean useHighlighter;
  public final String textFieldName;
  // public int[] docIDToID;
  public final boolean hasDeletions;
  public final TaxonomyReader taxoReader;
  public final FacetsConfig facetsConfig;
  // maps facet dimension to method (sortedset, taxonomy)
  public final Map<String, Integer> facetFields;
  public final Map<Object, ThreadLocal<PKLookupState>> pkLookupStates = new HashMap<>();
  public final Map<Object, ThreadLocal<PointsPKLookupState>> pointsPKLookupStates = new HashMap<>();
  private SortedSetDocValuesReaderState sortedSetReaderState;

  public IndexState(ReferenceManager<IndexSearcher> mgr, TaxonomyReader taxoReader, String textFieldName,
      DirectSpellChecker spellChecker, String hiliteImpl, FacetsConfig facetsConfig, Map<String, Integer> facetFields)
      throws IOException {
    this.mgr = mgr;
    this.spellChecker = spellChecker;
    this.textFieldName = textFieldName;
    this.taxoReader = taxoReader;
    this.facetsConfig = facetsConfig;
    this.facetFields = facetFields;

    groupEndQuery = new TermQuery(new Term("groupend", "x"));
    if (hiliteImpl.equals("FastVectorHighlighter")) {
      fastHighlighter = new FastVectorHighlighter(true, true);
      useHighlighter = false;
    } else if (hiliteImpl.equals("PostingsHighlighter")) {
      throw new IllegalArgumentException("fix me!  switch to UnifiedHighlighter");
    } else if (hiliteImpl.equals("Highlighter")) {
      fastHighlighter = null;
      useHighlighter = true;
    } else {
      throw new IllegalArgumentException("unrecognized -hiliteImpl \"" + hiliteImpl + "\"");
    }
    IndexSearcher searcher = mgr.acquire();
    try {
      hasDeletions = searcher.getIndexReader().hasDeletions();

      for (LeafReaderContext ctx : searcher.getIndexReader().leaves()) {
        pkLookupStates.put(ctx.reader().getCoreCacheHelper().getKey(), new ThreadLocal<PKLookupState>());
        pointsPKLookupStates.put(ctx.reader().getCoreCacheHelper().getKey(), new ThreadLocal<PointsPKLookupState>());
      }
    } finally {
      mgr.release(searcher);
    }
  }

  private final Map<String, SortedSetDocValuesReaderState> ssdvFacetStates = new HashMap<>();

  public synchronized SortedSetDocValuesReaderState getSortedSetReaderState(String facetGroupField) throws IOException {
    SortedSetDocValuesReaderState result = ssdvFacetStates.get(facetGroupField);
    if (result == null) {
      IndexSearcher searcher = mgr.acquire();
      result = new DefaultSortedSetDocValuesReaderState(searcher.getIndexReader(), facetGroupField);
      // NOTE: do not release the acquired searcher here! Really we should have a
      // close() in this class where we release...
      ssdvFacetStates.put(facetGroupField, result);
    }

    return result;
  }

  /**
   * Holds re-used thread-private classes for postings primary key lookup for one
   * LeafReader
   */
  public static class PKLookupState {
    public final TermsEnum termsEnum;
    public final PostingsEnum postingsEnum;
    public final Bits liveDocs;

    public PKLookupState(LeafReader reader, String field) throws IOException {
      termsEnum = reader.terms(field).iterator();
      termsEnum.seekCeil(new BytesRef(""));
      postingsEnum = termsEnum.postings(null, 0);
      liveDocs = reader.getLiveDocs();
    }
  }

  public static class PKIntersectVisitor implements IntersectVisitor {
    private final byte[] targetValue = new byte[Integer.BYTES];
    public int answer;

    public void reset(int value) {
      IntPoint.encodeDimension(value, targetValue, 0);
      answer = -1;
    }

    @Override
    public void visit(int docID) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void visit(int docID, byte[] packedValue) throws IOException {
      if (Arrays.equals(packedValue, targetValue)) {
        answer = docID;
      }
    }

    @Override
    public Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
      // if (StringHelper.compare(Integer.BYTES, targetValue, 0, minPackedValue, 0) < 0) {
      //   return Relation.CELL_OUTSIDE_QUERY;
      // }
      // if (StringHelper.compare(Integer.BYTES, targetValue, 0, maxPackedValue, 0) > 0) {
      //   return Relation.CELL_OUTSIDE_QUERY;
      // }
      return Relation.CELL_CROSSES_QUERY;
    }
  }

  /**
   * Holds re-used thread-private classes for points primary key lookup for one
   * LeafReader
   */
  public static class PointsPKLookupState {
    public final PKIntersectVisitor visitor;
    public final IntersectState state;
    public final Bits liveDocs;
    public final BKDReader bkdReader;

    public PointsPKLookupState(LeafReader reader, String fieldName) throws IOException {
      visitor = new PKIntersectVisitor();
      bkdReader = ((BKDReader) reader.getPointValues(fieldName));
      state = bkdReader.getIntersectState(visitor);
      liveDocs = reader.getLiveDocs();
    }
  }

  /*
   * public void setDocIDToID() throws IOException { long t0 =
   * System.currentTimeMillis(); IndexSearcher searcher = mgr.acquire(); try {
   * docIDToID = new int[searcher.getIndexReader().maxDoc()]; int base = 0;
   * for(AtomicReaderContext sub : searcher.getIndexReader().leaves()) { int
   * maxDoc = sub.reader().maxDoc(); for(int doc=0;doc<maxDoc;doc++) { // NOTE:
   * slow!!!! But we do this once on startup ... docIDToID[base+doc] =
   * LineFileDocs.idToInt(sub.reader().document(doc).get("id")); } base += maxDoc;
   * } } finally { mgr.release(searcher); } long t1 = System.currentTimeMillis();
   * System.out.println((t1-t0) + " msec to set docIDToID"); }
   */
}