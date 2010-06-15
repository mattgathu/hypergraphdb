package hgtest.storage;

import hgtest.HGTestBase;

import java.util.ArrayList;
import java.util.List;

import org.hypergraphdb.HGBidirectionalIndex;
import org.hypergraphdb.HGHandle;
import org.hypergraphdb.HGLink;
import org.hypergraphdb.HGPersistentHandle;
import org.hypergraphdb.HGQuery;
import org.hypergraphdb.HGRandomAccessResult;
import org.hypergraphdb.HGSearchResult;
import org.hypergraphdb.HGSortIndex;
import org.hypergraphdb.HGTypeSystem;
import org.hypergraphdb.HyperGraph;
import org.hypergraphdb.HGQuery.hg;
import org.hypergraphdb.HGRandomAccessResult.GotoResult;
import org.hypergraphdb.algorithms.DefaultALGenerator;
import org.hypergraphdb.indexing.ByPartIndexer;
import org.hypergraphdb.indexing.HGIndexer;
import org.hypergraphdb.query.BFSCondition;
import org.hypergraphdb.query.HGQueryCondition;
import org.hypergraphdb.query.impl.HandleArrayResultSet;
import org.hypergraphdb.query.impl.InMemoryIntersectionResult;
import org.hypergraphdb.query.impl.LinkTargetsResultSet;
import org.hypergraphdb.query.impl.SortedIntersectionResult;
import org.hypergraphdb.query.impl.TraversalBasedQuery;
import org.hypergraphdb.query.impl.ZigZagIntersectionResult;
import org.hypergraphdb.storage.BAtoHandle;
import org.hypergraphdb.storage.BAtoString;
import org.hypergraphdb.util.Pair;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ResultSets extends HGTestBase
{
    private static final String ALIAS_PREFIX = "TestIntAlias";
    static String HG_PATH = "c:/temp/graphs/test";
    final static int COUNT = 10;
    final static int ALIAS_COUNT = 5;
    HGSortIndex<Integer, HGHandle> index;

    public static void main(String[] args)
    {
        new ResultSets().test();
    }

    private boolean index_rs_fixed = false;

    public void test()
    {
        setUp();
        if (index_rs_fixed)
        {
            testSingleValueResultSet();
            testKeyScanResultSet();
            testKeyRangeForwardResultSet();
            testKeyRangeBackwardResultSet();
            testSingleKeyResultSet();
            testZigZagAndInMemoryIntersectionResult();
        }
        testSortedIntersectionResult();
        testAlGenerator();
        testUnionResult();
        testFilteredResultSet();
        testTraversalResult();
        testLinkTargetsRSAndHandleArrayRS();
        tearDown();
    }

    @BeforeClass
    public void setUp()
    {
        super.setUp();
        HGTypeSystem ts = graph.getTypeSystem();
        HGHandle typeH = ts.getTypeHandle(TestInt.class);
        for (int i = 0; i < ALIAS_COUNT; i++)
            ts.addAlias(typeH, ALIAS_PREFIX + i);

        index = (HGSortIndex<Integer, HGHandle>) graph.getIndexManager()
                .<Integer, HGHandle> register(new ByPartIndexer(typeH, "x"));
        for (int i = 0; i < COUNT; i++)
            graph.add(new TestInt(i));
    }

    @AfterClass
    public void tearDown()
    {
        List<HGHandle> list = hg.findAll(graph, hg.type(TestInt.class));
        for (HGHandle handle : list)
            graph.remove(handle);
        list = hg.findAll(graph, hg.type(TestLink.Int.class));
        for (HGHandle handle : list)
            graph.remove(handle);
        HGTypeSystem ts = graph.getTypeSystem();
        for (int i = 0; i < ALIAS_COUNT; i++)
            try
            {
                ts.removeAlias(ALIAS_PREFIX + i);
            }
            catch (Throwable t)
            {
            }
        List<HGIndexer> indexers = graph.getIndexManager().getIndexersForType(
                graph.getTypeSystem().getTypeHandle(TestInt.class));
        if (indexers != null) for (HGIndexer indexer : indexers)
            graph.getIndexManager().deleteIndex(indexer);
        super.tearDown();
    }

    @Test
    public void testUnionResult()
    {
        HGSearchResult<HGHandle> res = graph.find(hg.and(
                hg.type(TestInt.class), hg.or(hg.eq(new TestInt(9)), hg
                        .eq(new TestInt(5)))));
        try
        {
            Assert.assertTrue(expectedType(res, "UnionResult"));
            List<Integer> list = result__list(graph, res);
            Assert.assertEquals(list.size(), 2);
            List<Integer> back_list = back_result__list(graph, res);
            Assert.assertTrue(reverseLists(list, back_list));
        }
        finally
        {
            res.close();
        }
    }

    @Test
    public void testZigZagAndInMemoryIntersectionResult()
    {
        zigzag_or_in_memory_test(true);
        zigzag_or_in_memory_test(false);
    }

    private void zigzag_or_in_memory_test(boolean zigzag_or_in_memory)
    {
        HGRandomAccessResult<HGHandle> res = (zigzag_or_in_memory) ? new ZigZagIntersectionResult<HGHandle>()
                : new InMemoryIntersectionResult<HGHandle>();
        try
        {
            HGRandomAccessResult<HGHandle> left = index.findGTE(9);
            HGRandomAccessResult<HGHandle> right = index.findGTE(8);
            if (zigzag_or_in_memory) ((ZigZagIntersectionResult) res).init(
                    left, right);
            else
                ((InMemoryIntersectionResult) res).init(left, right);
            // List<Integer> left_list = result__list(graph, left);
            // List<Integer> right_list = result__list(graph, right);
            // left.goBeforeFirst(); right.goBeforeFirst();
            if (true)
            {
                HGHandle l = left.next();
                GotoResult gt = right.goTo(l, false);
                GotoResult gt1 = right.goTo(l, true);
                System.out.println("L:" + l + "GT:" + gt + ":" + gt1);
            }
            List<Integer> list = result__list(graph, res);
            // Assert.assertEquals(list.size(), 2);
            if (list.size() != 1)
            {
                res.goBeforeFirst();
                while (left.hasNext())
                    System.out.println("L:" + left.next());
                while (right.hasNext())
                    System.out.println("R:" + right.next());
            }
            // List<Integer> back_list = back_result__list(graph, res);
            // Assert.assertTrue(reverseLists(list, back_list));
            checkBeforeFirstAfterLastNotEmptyRS(res);
        }
        finally
        {
            res.close();
        }
    }

    @Test
    public void testAlGenerator()
    {
        HGHandle needH = create_simple_subgraph();
        DefaultALGenerator gen = new DefaultALGenerator(graph, null, null);
        HGSearchResult<Pair<HGHandle, HGHandle>> i = gen.generate(needH);
        while (i.hasNext())
        {
            Assert.assertNotNull(i.next().getFirst());
        }
    }

    @Test
    public void testTraversalResult()
    {
        HGHandle needH = create_simple_subgraph();
        BFSCondition rs = hg.bfs(needH);
        TraversalBasedQuery tbs = new TraversalBasedQuery(rs
                .getTraversal(graph), TraversalBasedQuery.ReturnType.both);
        int both = countRS(tbs.execute(), true);
        tbs = new TraversalBasedQuery(rs.getTraversal(graph),
                TraversalBasedQuery.ReturnType.links);
        int links = countRS(tbs.execute(), true);
        tbs = new TraversalBasedQuery(rs.getTraversal(graph),
                TraversalBasedQuery.ReturnType.targets);
        int targets = countRS(tbs.execute(), true);
        Assert.assertEquals(both, targets);
        // 2 links + 2 targets
        Assert.assertEquals(links, targets);
        // TODO: we could check for duplicates in the links RS
    }

    @Test
    public void testSingleValueResultSet()
    {
        HGBidirectionalIndex<String, HGPersistentHandle> idx = graph.getStore()
                .getBidirectionalIndex(
                /* HGTypeSystem.TYPE_ALIASES_DB_NAME, */
                "hg_typesystem_type_alias", BAtoString.getInstance(),
                        BAtoHandle.getInstance(), null);
        HGRandomAccessResult<String> res = idx.findByValue(graph
                .getPersistentHandle(graph.getTypeSystem().getTypeHandle(
                        TestInt.class)));
        try
        {
            Assert.assertTrue(expectedType(res, "SingleValueResultSet"));
            List<String> list = new ArrayList<String>();
            while (res.hasNext())
                list.add(res.next());
            Assert.assertEquals(list.size(), ALIAS_COUNT);
            List<String> back_list = new ArrayList<String>();
            back_list.add(res.current());
            while (res.hasPrev())
                back_list.add(res.prev());
            // print(list); print(back_list);
            Assert.assertTrue(reverseLists(list, back_list));
            // checkBeforeFirstAfterLastNotEmptyRS((HGRandomAccessResult) res);
        }
        finally
        {
            res.close();
        }
    }

    @Test
    public void testKeyRangeForwardResultSet()
    {
        HGRandomAccessResult<HGHandle> res = index.findGTE(5);
        try
        {
            Assert.assertTrue(expectedType(res, "KeyRangeForwardResultSet"));
            checkBeforeFirstAfterLastNotEmptyRS(res);
            List<Integer> list = result__list(graph, res);
            Assert.assertTrue(isSortedList(list, true));
            Assert.assertEquals(list.size(), 5);
            List<Integer> back_list = back_result__list(graph, res);
            // print(list); print(back_list);
            Assert.assertTrue(reverseLists(list, back_list));
            res.goBeforeFirst();
        }
        finally
        {
            res.close();
        }
        bounds_test(index.findGTE(-1));
    }

    @Test
    public void testKeyRangeBackwardResultSet()
    {
        HGRandomAccessResult<HGHandle> res = index.findLT(5);
        try
        {
            checkBeforeFirstAfterLastNotEmptyRS(res);
            Assert.assertTrue(expectedType(res, "KeyRangeBackwardResult"));
            List<Integer> list = result__list(graph, res);
            Assert.assertTrue(isSortedList(list, false));
            Assert.assertEquals(list.size(), 5);
            List<Integer> back_list = back_result__list(graph, res);
            // print(list); print(back_list);
            Assert.assertTrue(reverseLists(list, back_list));
        }
        finally
        {
            res.close();
        }
        bounds_test(index.findLT(10));
    }

    @Test
    public void testFilteredResultSet()
    {
        Assert.assertEquals(hg.findAll(graph, hg.type(TestInt.class)).size(),
                10);
        HGQueryCondition cond = hg.lte(new TestInt(5));
        // hg.and(hg.type(TestInt.class), hg.lte("x", 5));
        HGQuery<HGHandle> q = HGQuery.make(graph, cond);
        HGSearchResult<HGHandle> res = q.execute();
        try
        {
            // Assert.assertTrue(expectedType(res, "FilteredResultSet"));
            List<Integer> list = result__list(graph, res);
            Assert.assertEquals(list.size(), COUNT - 5 + 1);
            List<Integer> back_list = back_result__list(graph, res);
            // print(list); print(back_list);
            Assert.assertTrue(reverseLists(list, back_list));
        }
        finally
        {
            res.close();
        }

        res = graph.find(hg.lte(new TestInt(10)));
        bounds_test(res);
        res = graph.find(hg.gte(new TestInt(-1)));
        bounds_test(res);
    }

    @Test
    public void testSortedIntersectionResult()
    {
        // test with sorted sets
        testSorted(index.findLTE(5), index.findLTE(7), true);

        // test with unsorted sets
        HGQueryCondition cond = hg.lte(new TestInt(5));
        HGQuery<HGHandle> q = HGQuery.make(graph, cond);
        HGSearchResult<HGHandle> left = q.execute();
        cond = hg.lte(new TestInt(7));
        q = HGQuery.make(graph, cond);
        HGSearchResult<HGHandle> right = q.execute();
        // we didn't test for sorted result set here...
        testSorted(left, right, false);
    }

    @Test
    public void testLinkTargetsRSAndHandleArrayRS()
    {
        HGHandle needH = graph.add(new TestInt(1200));
        HGLink link = new TestLink(needH);
        HGHandle linkH = graph.add(link);
        testL(new LinkTargetsResultSet(link));
        HGPersistentHandle[] A = graph.getStore().getLink(
                graph.getPersistentHandle(linkH));
        testL(new HandleArrayResultSet(A, 2));
    }

    private void testL(HGSearchResult<HGHandle> res)
    {
        try
        {
            List<Integer> list = result__list(graph, res);
            Assert.assertEquals(list.size(), 3);
            List<Integer> back_list = back_result__list(graph, res);
            Assert.assertTrue(reverseLists(list, back_list));
        }
        finally
        {
            res.close();
        }
    }

    private void testSorted(HGSearchResult<HGHandle> left,
            HGSearchResult<HGHandle> right, boolean assert_sorted)
    {
        HGSearchResult<HGHandle> res = new SortedIntersectionResult<HGHandle>(
                left, right);

        List<Integer> list = result__list(graph, res);
        Assert.assertEquals(list.size(), 6);
        List<Integer> back_list = back_result__list(graph, res);
        Assert.assertTrue(reverseLists(list, back_list));
        if (assert_sorted) Assert.assertTrue(isSortedList(list, false));
        bounds_test(res);
    }

    void bounds_test(HGSearchResult<HGHandle> res)
    {
        try
        {
            Assert.assertTrue(!res.hasPrev());
            Assert.assertTrue(res.hasNext());
            // print(result__list(graph, res));
        }
        finally
        {
            res.close();
        }
    }

    @Test
    public void testSingleKeyResultSet()
    {
        HGQueryCondition cond = hg.and(hg.type(TestInt.class));
        HGQuery<HGHandle> q = HGQuery.make(graph, cond);
        HGRandomAccessResult<HGHandle> res = (HGRandomAccessResult<HGHandle>) q
                .execute();
        try
        {
            checkBeforeFirstAfterLastNotEmptyRS(res);
            Assert.assertTrue(expectedType(res, "SingleKeyResultSet"));
            List<Integer> list = result__list(graph, res);
            Assert.assertEquals(list.size(), COUNT);
            List<Integer> back_list = back_result__list(graph, res);
            // print(list); print(back_list);
            Assert.assertTrue(reverseLists(list, back_list));

        }
        finally
        {
            res.close();
        }
    }

    @Test
    public void testKeyScanResultSet()
    {
        HGSearchResult<Integer> res = index.scanKeys();
        try
        {
            Assert.assertTrue(expectedType(res, "KeyScanResultSet"));
            // checkBeforeFirstAfterLastNotEmptyRS(res);
            List<Integer> list = new ArrayList<Integer>();
            while (res.hasNext())
                list.add(res.next());
            Assert.assertEquals(list.size(), COUNT);
            List<Integer> back_list = new ArrayList<Integer>();
            back_list.add(res.current());
            while (res.hasPrev())
                back_list.add(res.prev());
            // print(list); print(back_list);
            Assert.assertTrue(reverseLists(list, back_list));
        }
        finally
        {
            res.close();
        }
    }

    private void checkBeforeFirstAfterLastNotEmptyRS(
            HGRandomAccessResult<HGHandle> res)
    {
        // check goTo() for all the elements in the result set
        List<HGHandle> handles = new ArrayList<HGHandle>();
        while (res.hasNext())
            handles.add(res.next());
        int i = 0;
        for (HGHandle h : handles)
        {
            // Assert.assertEquals(GotoResult.found, res.goTo(h, false));
            if (res.goTo(h, !false) != GotoResult.found)
                System.out
                        .println("Problem in: " + i + " of " + handles.size());
            i++;
        }
        res.goAfterLast();
        Assert.assertFalse(res.hasNext());
        Assert.assertNotNull(res.prev());
        res.goBeforeFirst();
        Assert.assertFalse(res.hasPrev());
        Assert.assertNotNull(res.next());
    }

    static List<Integer> result__list(HyperGraph graph,
            HGSearchResult<HGHandle> res)
    {
        List<Integer> list = new ArrayList<Integer>();
        while (res.hasNext())
            list.add(((TestInt) graph.get(res.next())).getX());
        return list;
    }

    static boolean expectedType(Object o, String t)
    {
        return o.getClass().getName().indexOf(t) > -1;
    }

    static boolean isSortedList(List<Integer> list, boolean up)
    {
        if (list.isEmpty()) return true;
        int curr = list.get(0);
        for (int i = 1; i < list.size(); i++)
        {
            if (up && curr < list.get(i) || !up && curr > list.get(i)) curr = list
                    .get(i);
            else
                return false;
        }
        return true;
    }

    static boolean reverseLists(List<?> list, List<?> other)
    {
        if (list.size() != other.size()) return false;
        int size = list.size();
        for (int i = 0; i < list.size(); i++)
            if (!list.get(i).equals(other.get(size - 1 - i))) return false;
        return true;
    }

    static List<Integer> back_result__list(HyperGraph graph,
            HGSearchResult<HGHandle> res)
    {
        List<Integer> list = new ArrayList<Integer>();
        // add the last result which is current right now
        Integer o = ((TestInt) graph.get(res.current())).getX();
        list.add(o);
        while (res.hasPrev())
            list.add(((TestInt) graph.get(res.prev())).getX());
        return list;
    }

    static void print(HyperGraph graph, HGSearchResult<HGHandle> res)
    {
        // System.out.println("HGSearchResult: " + res);
        List<Integer> list = result__list(graph, res);
        print(list);
    }

    static void print(List<?> list)
    {
        System.out.println("Reversed list");
        for (int i = 0; i < list.size(); i++)
            System.out.println(":" + i + ":" + list.get(i));
    }

    private HGHandle create_simple_subgraph()
    {
        HGHandle linkH = graph.add(new TestLink(graph.add(35), graph
                .add("Bizi")));
        HGHandle linkH1 = graph.add(new TestLink(graph.add("Bobi"), graph
                .add("Other"), linkH));
        return linkH;
    }

    private int countRS(HGSearchResult res, boolean close)
    {
        int i = 0;
        while (res.hasNext())
        {
            System.out.println(res.next());
            i++;
        }
        if (close) res.close();
        return i;
    }

}
