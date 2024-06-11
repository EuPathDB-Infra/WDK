package org.gusdb.wdk.model.dataset;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.EncryptionUtil;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkRuntimeException;

import java.io.*;

public class DatasetFileContents extends DatasetContents {

  private static final Logger LOG = Logger.getLogger(DatasetFileContents.class);

  private static final int BUF_SIZE = 8192;

  /**
   * Backing file containing the contents of this dataset.
   */
  private final File contents;

  /**
   * Whether or not this class is the "owner" of the backing
   * file.
   *
   * If this class _is_ the owner, then the tempfile may be
   * deleted when/if the class is finalized.
   */
  private final boolean owned;

  /**
   * Lazily evaluated MD5 checksum of this dataset's
   * contents.
   */
  private String checksum;

  /**
   * Number of records expected in this dataset file
   * (some files are written knowing how many records are contained within)
   */
  private final Long numRecords;

  public DatasetFileContents(
    final String fileName,
    final File contents
  ) {
    this(fileName, contents, null);
  }

  public DatasetFileContents(
      final String fileName,
      final File contents,
      final Long numRecords) {
    super(fileName);
    LOG.info("Created new DatasetFileContents object pointing at file: " + contents.getAbsolutePath());
    this.contents = contents;
    this.owned = false;
    this.numRecords = numRecords;
  }

  DatasetFileContents(
    final String fileName,
    final Reader stream
  ) throws IOException {
    super(fileName);
    final var buf = new char[BUF_SIZE];
    final var tmp = File.createTempFile("dataset-", "-" + fileName);

    try (final var out = new FileWriter(tmp)) {

      var read = 0;
      while (read > -1) {
        read = stream.read(buf, 0, BUF_SIZE);
        out.write(buf, 0, read);
      }
    }

    tmp.deleteOnExit();
    this.owned = true;
    this.contents = tmp;
    this.numRecords = null;
  }

  /**
   * When or if this method will be called is entirely up to
   * the garbage collector.
   *
   * If it is ever called, then it will delete the tmp file
   * created by this instance.  If it is never called, the
   * file will be deleted on site restart or on server
   * hardware reboot.
   */
  @Override
  @SuppressWarnings({ "ResultOfMethodCallIgnored" })
  protected void finalize() {
    if (owned)
      contents.delete();
  }

  @Override
  public String getChecksum() {
    if (checksum != null)
      return checksum;

    return checksum = genChecksum(contents);
  }

  @Override
  public Reader getContentReader() throws WdkModelException {
    try {
      return new FileReader(contents);
    } catch (FileNotFoundException e) {
      throw new WdkModelException(e);
    }
  }

  @Override
  public InputStream getContentStream() throws WdkModelException {
    try {
      return new FileInputStream(contents);
    } catch (FileNotFoundException e) {
      throw new WdkModelException(e);
    }
  }

  private static String genChecksum(final File file) {
    try {
      var dig  = EncryptionUtil.newMd5Digester();
      var str  = new BufferedInputStream(new FileInputStream(file), BUF_SIZE);
      var buf  = new byte[BUF_SIZE];

      var read = 0;
      read = str.read(buf);
      while (read > 0) {
        dig.update(buf, 0, read);
        read = str.read(buf);
      }

      str.close();
      return EncryptionUtil.convertToHex(dig.digest(), true);
    } catch (IOException e) {
      throw new WdkRuntimeException(e);
    }
  }

  @Override
  public long getEstimatedRowCount() {
    return numRecords != null
        ? numRecords
        : (contents.length() / ESTIMATED_BYTES_PER_ID) + 1; // round up
  }
}
