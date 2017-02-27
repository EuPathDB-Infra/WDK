package org.gusdb.wdk.jmx.mbeans.dbms;

import org.gusdb.fgputil.db.pool.DatabaseInstance;

public class OracleDbInfo extends AbstractDbInfo {

  // NOTE: column names will be lower-cased keys in metaDataMap
  private static final String METADATA_SQL = new StringBuilder()
    .append(" select                                                          ")
    .append(" global_name,                                                    ")
    .append(" ver.banner version,                                             ")
    .append(" to_char(sysdate, 'Dy DD-Mon-YYYY HH24:MI:SS') system_date,      ")
    .append(" sys_context('USERENV', 'SESSION_USER'       ) login,            ")
    .append(" sys_context('userenv', 'SERVICE_NAME'       ) service_name,     ")
    .append(" sys_context('userenv', 'DB_NAME'            ) db_name,          ")
    .append(" sys_context('USERENV', 'DB_UNIQUE_NAME'     ) db_unique_name,   ")
    .append(" sys_context('USERENV', 'INSTANCE_NAME'      ) instance_name,    ")
    .append(" sys_context('USERENV', 'DB_DOMAIN'          ) db_domain,        ")
    .append(" sys_context('USERENV', 'HOST'               ) client_host,      ")
    .append(" sys_context('USERENV', 'OS_USER'            ) os_user,          ")
    .append(" sys_context('USERENV', 'CURRENT_USERID'     ) current_userid,   ")
    .append(" sys_context('USERENV', 'SESSION_USER'       ) session_user,     ")
    .append(" sys_context('USERENV', 'SESSION_USERID'     ) session_userid,   ")
    .append(" (select value from nls_database_parameters                      ")
    .append("     where parameter='NLS_CHARACTERSET') character_encoding      ")
    .append(" from global_name, v$version ver                                 ")
    .append(" where lower(ver.banner) like '%oracle%'                         ")
    .toString();
  
  private static final String SERVERNAME_SQL = new StringBuilder()
    .append(" select                                     ")
    .append(" UTL_INADDR.get_host_name as server_name,   ")
    .append(" UTL_INADDR.get_host_address as server_ip   ")
    .append(" from dual                                  ")
    .toString();

  private static final String DBLINK_SQL = new StringBuilder()
    .append(" select             ")
    .append(" owner owner,       ")
    .append(" db_link db_link,   ")
    .append(" username username, ")
    .append(" host host,         ")
    .append(" created created    ")
    .append(" from all_db_links  ")
    .append(" order by db_link   ")
    .toString();

  // *.dbf files on disk; not counting redo logs and control files. If
  // you want those,
  // .append("    ( select sum(bytes)/1024/1024/1024 redo_size from sys.v_$log ) +                             ")
  // .append("    ( select sum(BLOCK_SIZE*FILE_SIZE_BLKS)/1024/1024/1024 controlfile_size from v$controlfile)  ")
  private static final String DBF_SIZE_ON_DISK = new StringBuilder()
    .append("  select ROUND(                                                                 ")
    .append("    ( select sum(bytes)/1024/1024/1024 data_size from dba_data_files ) +        ")
    .append("    ( select nvl(sum(bytes),0)/1024/1024/1024 temp_size from dba_temp_files )   ")
    .append("  ) as dbf_gb_on_disk from dual                                                 ")
    .toString();

  public OracleDbInfo(DatabaseInstance db) {
    super(db);
  }

  @Override
  protected String getMetaDataSql() {
    return METADATA_SQL;
  }

  @Override
  protected String getServerNameSql() {
    return SERVERNAME_SQL;
  }

  @Override
  protected String getDblinkSql() {
    return DBLINK_SQL;
  }

  @Override
  protected String getDbfSizeOnDisk() {
    return DBF_SIZE_ON_DISK;
  }

  @Override
  protected String getDbLinkValidationSql(String dblink) {
    return new StringBuilder("select 1 from dual@").append(dblink).toString();
  }

}
