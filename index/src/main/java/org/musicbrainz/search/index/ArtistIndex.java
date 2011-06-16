/*
 * MusicBrainz Search Server
 * Copyright (C) 2009  Lukas Lalinsky

 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package org.musicbrainz.search.index;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.musicbrainz.mmd2.Tag;
import org.musicbrainz.search.MbDocument;
import org.musicbrainz.search.analysis.MusicbrainzSimilarity;

import java.io.IOException;
import java.sql.*;
import java.util.*;

public class ArtistIndex extends DatabaseIndex {

    public static final String INDEX_NAME = "artist";

    //Special purpose Artist
    private static final String DELETED_ARTIST_MBID = "c06aa285-520e-40c0-b776-83d2c9e8a6d1";

    public ArtistIndex(Connection dbConnection) throws SQLException {
        super(dbConnection);
    }

    public ArtistIndex() {
    }

    public String getName() {
        return ArtistIndex.INDEX_NAME;
    }

    public Analyzer getAnalyzer() {
        return super.getAnalyzer(ArtistIndexField.class);
    }

	@Override
	public IndexField getIdentifierField() {
		return ArtistIndexField.ID;
	}
    
    public int getMaxId() throws SQLException {
        Statement st = dbConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT MAX(id) FROM artist");
        rs.next();
        return rs.getInt(1);
    }

    public int getNoOfRows(int maxId) throws SQLException {
    	PreparedStatement st = dbConnection.prepareStatement(
    			"SELECT count(*) FROM artist WHERE id <= ? AND gid <> ?::uuid");
    	st.setInt(1, maxId);
    	st.setString(2, DELETED_ARTIST_MBID);
        ResultSet rs = st.executeQuery();
        rs.next();
        return rs.getInt(1);
    }

    @Override
    public void init(IndexWriter indexWriter, boolean isUpdater) throws SQLException {

        indexWriter.setSimilarity(new MusicbrainzSimilarity());

        addPreparedStatement("TAGS",
                "SELECT artist_tag.artist, tag.name as tag, artist_tag.count as count " +
                " FROM artist_tag " +
                "  INNER JOIN tag ON tag=id " +
                " WHERE artist between ? AND ?");

        addPreparedStatement("ALIASES",
                "SELECT artist_alias.artist as artist, n.name as alias " +
                " FROM artist_alias " +
                "  JOIN artist_name n ON (artist_alias.name = n.id) " +
                " WHERE artist BETWEEN ? AND ? " +
                "UNION " +
                "SELECT artist as artist, n.name as alias " +
                " FROM artist_credit_name " +
                "  JOIN artist_name n ON n.id = artist_credit_name.name " +
                " WHERE artist BETWEEN ? AND ? ");


        addPreparedStatement("ARTISTS",
                "SELECT artist.id, gid, n0.name as name, n1.name as sort_name, " +
                "  artist_type.name as type, begin_date_year, begin_date_month, begin_date_day, " +
                "  end_date_year, end_date_month, end_date_day, " +
                "  comment, lower(iso_code) as country, lower(gender.name) as gender, ipi_code " +
                " FROM artist " +
                "  LEFT JOIN artist_name n0 ON artist.name = n0.id " +
                "  LEFT JOIN artist_name n1 ON artist.sort_name = n1.id " +
                "  LEFT JOIN artist_type ON artist.type = artist_type.id " +
                "  LEFT JOIN country ON artist.country = country.id " +
                "  LEFT JOIN gender ON artist.gender=gender.id " +
                " WHERE artist.id BETWEEN ? AND ?");
    }

    public void indexData(IndexWriter indexWriter, int min, int max) throws SQLException, IOException {

        // Get Tags
        PreparedStatement st = getPreparedStatement("TAGS");
        st.setInt(1, min);
        st.setInt(2, max);
        ResultSet rs = st.executeQuery();
        Map<Integer,List<Tag>> tags = TagHelper.completeTagsFromDbResults(rs,"artist");
        rs.close();

        //Aliases (and Artist Credits)
        Map<Integer, Set<String>> aliases = new HashMap<Integer, Set<String>>();
        st = getPreparedStatement("ALIASES");
        st.setInt(1, min);
        st.setInt(2, max);
        st.setInt(3, min);
        st.setInt(4, max);
        rs = st.executeQuery();
        while (rs.next()) {
            int artistId = rs.getInt("artist");
            Set<String> list;
            if (!aliases.containsKey(artistId)) {
                list = new HashSet<String>();
                aliases.put(artistId, list);
            } else {
                list = aliases.get(artistId);
            }
            list.add(rs.getString("alias"));
        }
        rs.close();

        st = getPreparedStatement("ARTISTS");
        st.setInt(1, min);
        st.setInt(2, max);
        rs = st.executeQuery();
        while (rs.next()) {
            if(rs.getString("gid").equals(DELETED_ARTIST_MBID))
            {
                continue;
            }
            indexWriter.addDocument(documentFromResultSet(rs, tags, aliases));
        }
        rs.close();
    }

    public Document documentFromResultSet(ResultSet rs,
                                          Map<Integer,List<Tag>> tags,
                                          Map<Integer, Set<String>> aliases) throws SQLException {

        MbDocument doc = new MbDocument();
        int artistId = rs.getInt("id");
        String artistGuid = rs.getString("gid");
        doc.addField(ArtistIndexField.ID, artistId);
        doc.addField(ArtistIndexField.ARTIST_ID, artistGuid);
        doc.addField(ArtistIndexField.ARTIST, rs.getString("name"));
        doc.addField(ArtistIndexField.SORTNAME, rs.getString("sort_name"));

        //Allows you to search for artists of unknown type
        String type = rs.getString("type");
        if (type != null) {
            doc.addField(ArtistIndexField.TYPE, type);
        } else {
            doc.addField(ArtistIndexField.TYPE, ArtistType.UNKNOWN.getName());
        }

        doc.addNonEmptyField(ArtistIndexField.BEGIN,
                Utils.formatDate(rs.getInt("begin_date_year"), rs.getInt("begin_date_month"), rs.getInt("begin_date_day")));

        doc.addNonEmptyField(ArtistIndexField.END,
                Utils.formatDate(rs.getInt("end_date_year"), rs.getInt("end_date_month"), rs.getInt("end_date_day")));

        doc.addNonEmptyField(ArtistIndexField.COMMENT, rs.getString("comment"));
        doc.addNonEmptyField(ArtistIndexField.COUNTRY, rs.getString("country"));
        doc.addNonEmptyField(ArtistIndexField.GENDER, rs.getString("gender"));

        doc.addNonEmptyField(ArtistIndexField.IPI, rs.getString("ipi_code"));

        if (aliases.containsKey(artistId)) {
            for (String alias : aliases.get(artistId)) {
                doc.addField(ArtistIndexField.ALIAS, alias);
            }
        }

        if (tags.containsKey(artistId)) {
            for (Tag tag : tags.get(artistId)) {
                doc.addField(ArtistIndexField.TAG, tag.getName());
                doc.addField(ArtistIndexField.TAGCOUNT, tag.getCount().toString());
            }
        }

        ArtistBoostDoc.boost(artistGuid, doc);
        return doc.getLuceneDocument();
    }

}
