

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

import ncsa.hdf.object.Dataset;
import ncsa.hdf.object.Datatype;
import ncsa.hdf.object.FileFormat;
import ncsa.hdf.object.Group;
import ncsa.hdf.object.h5.H5File;
import ncsa.hdf.utils.SetNatives;

/**
 * Test functionality to create and read hdf5 file 
 * 
 * @author Jesus R Martinez (jesus@metacell.us)
 *
 */
public class CreateFile {

	private static Log _logger = LogFactory.getLog(CreateFile.class);

	String fname  = "H5DatasetRead.h5";
	@Test
	/**
     * create the file and add groups and dataset into the file, which is the
     * same as javaExample.H5DatasetCreate
     * 
     * @see javaExample.HDF5DatasetCreate
     * @throws Exception
     */
    public void createFile() throws Exception {
		String path = System.getProperty("user.dir");;
		
		SetNatives.getInstance().setHDF5Native(path);
		
        long[] dims2D = { 20, 10 };
        
        // retrieve an instance of H5File
        FileFormat fileFormat = FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5);

        if (fileFormat == null) {
            _logger.error("Cannot find HDF5 FileFormat.");
            return;
        }

        // create a new file with a given file name.
        H5File testFile = (H5File) fileFormat.createFile(fname, FileFormat.FILE_CREATE_DELETE);

        if (testFile == null) {
            _logger.error("Failed to create file:" + fname);
            return;
        }

        // open the file and retrieve the root group
        testFile.open();
        Group root = (Group) ((javax.swing.tree.DefaultMutableTreeNode) testFile.getRootNode()).getUserObject();

        // set the data values
        int[] dataIn = new int[20 * 10];
        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 10; j++) {
                dataIn[i * 10 + j] = 1000 + i * 100 + j;
            }
        }

        // create 2D 32-bit (4 bytes) integer dataset of 20 by 10
        Datatype dtype = testFile.createDatatype(Datatype.CLASS_INTEGER, 4, Datatype.NATIVE, Datatype.NATIVE);
        Dataset dataset = testFile
                .createScalarDS("2D 32-bit integer 20x10", root, dtype, dims2D, null, null, 0, dataIn);

        // close file resource
        testFile.close();
    }
	
	private void readHDF5File() throws Exception {
		// retrieve an instance of H5File
        FileFormat fileFormat = FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5);

        if (fileFormat == null) {
            _logger.error("Cannot find HDF5 FileFormat.");
            return;
        }

        // open the file with read and write access
        FileFormat testFile = fileFormat.createInstance(fname, FileFormat.WRITE);

        if (testFile == null) {
           _logger.error("Failed to open file: " + fname);
            return;
        }

        // open the file and retrieve the file structure
        testFile.open();
        Group root = (Group) ((javax.swing.tree.DefaultMutableTreeNode) testFile.getRootNode()).getUserObject();

        // retrieve the dataset "2D 32-bit integer 20x10"
        Dataset dataset = (Dataset) root.getMemberList().get(0);
        int[] dataRead = (int[]) dataset.read();

        // print out the data values
        _logger.info("\n\nOriginal Data Values");
        for (int i = 0; i < 20; i++) {
            _logger.info("\n" + dataRead[i * 10]);
            for (int j = 1; j < 10; j++) {
                _logger.info(", " + dataRead[i * 10 + j]);
            }
        }

        // change data value and write it to file.
        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 10; j++) {
                dataRead[i * 10 + j]++;
            }
        }
        dataset.write(dataRead);

        // clean and reload the data value
        int[] dataModified = (int[]) dataset.read();

        // print out the modified data values
        _logger.info("\n\nModified Data Values");
        for (int i = 0; i < 20; i++) {
            _logger.info("\n" + dataModified[i * 10]);
            for (int j = 1; j < 10; j++) {
                System.out.print(", " + dataModified[i * 10 + j]);
            }
        }

        // close file resource
        testFile.close();
	}

}
