/**
 * 
 */
package org.gusdb.wdk.model.dbms;

import java.util.Date;

/**
 * @author xingao
 * 
 */
public class QueryInfo {

    private final String queryName;
    private final String queryChecksum;
    public final long creationDate;

    private int queryId;
    private String cacheTable;

    private boolean exist = false;

    public QueryInfo(String queryName, String checksum) {
      this.queryName = queryName;
      this.queryChecksum = checksum;
      creationDate = new Date().getTime();
    }

    /**
     * @return the queryId
     */
    public int getQueryId() {
        return queryId;
    }

    /**
     * @param queryId
     *            the queryId to set
     */
    public void setQueryId(int queryId) {
        this.queryId = queryId;
    }

    /**
     * @return the queryName
     */
    public String getQueryName() {
        return queryName;
    }

    /**
     * @return the cacheTable
     */
    public String getCacheTable() {
        return cacheTable;
    }

    /**
     * @param cacheTable
     *            the cacheTable to set
     */
    public void setCacheTable(String cacheTable) {
        this.cacheTable = cacheTable;
    }

    /**
     * @return the queryChecksum
     */
    public String getQueryChecksum() {
        return queryChecksum;
    }

    public boolean isExist() {
      return exist;
    }

    public void setExist(boolean exist) {
      this.exist = exist;
    }

    @Override
    public String toString() {
      return "query=" + queryName + ", id=" + queryId + ", exist=" + exist + ", checksum=" + queryChecksum;
    }
}
