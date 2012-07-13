package org.musicbrainz.search.servlet;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Before;
import org.junit.Test;
import org.musicbrainz.search.LuceneVersion;
import org.musicbrainz.search.MbDocument;
import org.musicbrainz.search.index.CDStubIndexField;
import org.musicbrainz.search.index.DatabaseIndex;
import org.musicbrainz.search.servlet.mmd2.CDStubWriter;
import org.musicbrainz.search.servlet.mmd2.ResultsWriter;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test retrieving Annotations entries from index and Outputting as Html
 */
public class FindCDStubTest {

    private SearchServer ss;
    private SearchServer sd;


    @Before
    public void setUp() throws Exception {
        RAMDirectory ramDir = new RAMDirectory();
        Analyzer analyzer = DatabaseIndex.getAnalyzer(CDStubIndexField.class);
        IndexWriterConfig writerConfig = new IndexWriterConfig(LuceneVersion.LUCENE_VERSION, analyzer);
        IndexWriter writer = new IndexWriter(ramDir, writerConfig);


        {
            MbDocument doc = new MbDocument();
            doc.addField(CDStubIndexField.ARTIST, "Doo Doo");
            doc.addField(CDStubIndexField.TITLE, "Doo Doo First");
            doc.addField(CDStubIndexField.BARCODE, "837101029193");
            doc.addField(CDStubIndexField.COMMENT, "CD Baby id:vozzolo");
            doc.addField(CDStubIndexField.DISCID, "qA87dKURKperVfmckD5b_xo8BO8-");
            doc.addField(CDStubIndexField.NUM_TRACKS, "2");
            writer.addDocument(doc.getLuceneDocument());
        }

        {
            MbDocument doc = new MbDocument();
            doc.addField(CDStubIndexField.TITLE, "fred");
            doc.addField(CDStubIndexField.DISCID, "w237dKURKperVfmckD5b_xo8BO8-");
            doc.addField(CDStubIndexField.NUM_TRACKS, "5");
            writer.addDocument(doc.getLuceneDocument());
        }

        writer.close();
        ss = new CDStubSearch(new IndexSearcher(IndexReader.open(ramDir)));
        sd = new CDStubDismaxSearch(new IndexSearcher(IndexReader.open(ramDir)));
    }

    @Test
    public void testSearchByArtist() throws Exception {
        Results res = ss.searchLucene("artist:\"Doo Doo\"", 0, 10);
        assertEquals(1, res.totalHits);
    }

    @Test
    public void testSearchByDismax1() throws Exception {
        Results res = sd.searchLucene("First", 0, 10);
        assertEquals(1, res.totalHits);
    }

    @Test
    public void testSearchByDismax2() throws Exception {
        Results res = sd.searchLucene("Doo Doo", 0, 10);
        assertEquals(1, res.totalHits);
    }

    @Test
    public void testSearchByDismax3() throws Exception {
        Results res = sd.searchLucene("837101029193", 0, 10);
        assertEquals(1, res.totalHits);
    }

    @Test
    public void testSearchByDismax4() throws Exception {
        Results res = sd.searchLucene("CD Baby", 0, 10);
        assertEquals(1, res.totalHits);
    }

    @Test
    public void testSearchByTitle() throws Exception {
        Results res = ss.searchLucene("title:\"Doo Doo First\"", 0, 10);
        assertEquals(1, res.totalHits);
    }

    @Test
    public void testSearchByBarcode() throws Exception {
        Results res = ss.searchLucene("barcode:\"837101029193\"", 0, 10);
        assertEquals(1, res.totalHits);
    }

    @Test
    public void testSearchByComment() throws Exception {
        Results res = ss.searchLucene("comment:\"CD Baby id:vozzolo\"", 0, 10);
        assertEquals(1, res.totalHits);
    }

    @Test
    public void testSearchByDiscId() throws Exception {
        Results res = ss.searchLucene("discid:qA87dKURKperVfmckD5b_xo8BO8-", 0, 10);
        assertEquals(1, res.totalHits);
    }

    @Test
    public void testSearchByNumTracks() throws Exception {
        Results res = ss.searchLucene("tracks:2", 0, 10);
        assertEquals(1, res.totalHits);
    }


    /**
     * @throws Exception
     */
    @Test
    public void testOutputXml() throws Exception {

        Results res = ss.searchLucene("title:\"Doo Doo\"", 0, 1);
        ResultsWriter writer = new CDStubWriter();
        StringWriter sw = new StringWriter();
        PrintWriter pr = new PrintWriter(sw);
        writer.write(pr, res);
        pr.close();

        String output = sw.toString();
        System.out.println("Xml is" + output);
        assertTrue(output.contains("xmlns:ext=\"http://musicbrainz.org/ns/ext#-2.0\""));
        assertTrue(output.contains("count=\"1\""));
        assertTrue(output.contains("offset=\"0\""));
        assertTrue(output.contains("score=\"100\""));
        assertTrue(output.contains("id=\"qA87dKURKperVfmckD5b_xo8BO8-\""));
        assertTrue(output.contains("<title>Doo Doo First</title>"));
        assertTrue(output.contains("<artist>Doo Doo</artist>"));
        assertTrue(output.contains("<barcode>837101029193</barcode>"));
        assertTrue(output.contains("<comment>CD Baby id:vozzolo</comment>"));
        assertTrue(output.contains("<track-list count=\"2\"/>"));

    }

    /**
     * @throws Exception
     */
    @Test
    public void testOutputXmlNoArtist() throws Exception {

        Results res = ss.searchLucene("title:fred", 0, 1);
        ResultsWriter writer = new CDStubWriter();
        StringWriter sw = new StringWriter();
        PrintWriter pr = new PrintWriter(sw);
        writer.write(pr, res);
        pr.close();

        String output = sw.toString();
        System.out.println("Xml is" + output);
        assertTrue(output.contains("xmlns:ext=\"http://musicbrainz.org/ns/ext#-2.0\""));
        assertTrue(output.contains("count=\"1\""));
        assertTrue(output.contains("offset=\"0\""));
        assertTrue(output.contains("score=\"100\""));
        assertTrue(output.contains("id=\"w237dKURKperVfmckD5b_xo8BO8-\""));
        assertTrue(output.contains("<title>fred</title>"));
        //Pass empty artist element if empty coz expect search webpage always expects this field
        assertTrue(output.contains("<artist></artist>"));
        assertTrue(output.contains("<track-list count=\"5\"/>"));

    }

    @Test
    public void testOutputJson() throws Exception {

        Results res = ss.searchLucene("title:\"Doo Doo\"", 0, 1);
        ResultsWriter writer = new CDStubWriter();
        StringWriter sw = new StringWriter();
        PrintWriter pr = new PrintWriter(sw);
        writer.write(pr, res, SearchServerServlet.RESPONSE_JSON);
        pr.close();

        String output = sw.toString();
        System.out.println("Json is" + output);

        assertTrue(output.contains("\"count\":1"));
        assertTrue(output.contains("\"offset\":0,"));
        assertTrue(output.contains("\"score\":\"100\","));
        assertTrue(output.contains("\"id\":\"qA87dKURKperVfmckD5b_xo8BO8-\""));
        assertTrue(output.contains("\"title\":\"Doo Doo First\""));
        assertTrue(output.contains("\"artist\":\"Doo Doo\""));
        assertTrue(output.contains("\"barcode\":\"837101029193\""));
        assertTrue(output.contains("\"comment\":\"CD Baby id:vozzolo\""));
        assertTrue(output.contains("\"track-list\":{\"count\":2}"));

    }

    @Test
    public void testOutputJsonNew() throws Exception {

        Results res = ss.searchLucene("title:\"Doo Doo\"", 0, 1);
        ResultsWriter writer = new CDStubWriter();
        StringWriter sw = new StringWriter();
        PrintWriter pr = new PrintWriter(sw);
        writer.write(pr, res, SearchServerServlet.RESPONSE_JSON_NEW);
        pr.close();

        String output = sw.toString();
        System.out.println("Json New is" + output);


        assertTrue(output.contains("\"count\":1"));
        assertTrue(output.contains("\"offset\":0,"));
        assertTrue(output.contains("\"score\":\"100\","));
        assertTrue(output.contains("\"id\":\"qA87dKURKperVfmckD5b_xo8BO8-\""));
        assertTrue(output.contains("\"count\":2"));
        assertTrue(output.contains("\"title\":\"Doo Doo First\""));
        assertTrue(output.contains("\"artist\":\"Doo Doo\""));
        assertTrue(output.contains("\"barcode\":\"837101029193\""));
        assertTrue(output.contains("\"comment\":\"CD Baby id:vozzolo\""));
    }

    @Test
    public void testOutputJsonNewPretty() throws Exception {

        Results res = ss.searchLucene("title:\"Doo Doo\"", 0, 1);
        ResultsWriter writer = new CDStubWriter();
        StringWriter sw = new StringWriter();
        PrintWriter pr = new PrintWriter(sw);
        writer.write(pr, res, SearchServerServlet.RESPONSE_JSON_NEW, true);
        pr.close();

        String output = sw.toString();
        System.out.println("Json New Pretty is" + output);
        assertTrue(output.contains("\"offset\" : 0,"));

    }
}