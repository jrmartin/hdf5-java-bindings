/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                      *
 *                                                                           *
 * This file is part of the HDF Java Products distribution.                  *
 * The full copyright notice, including terms governing use, modification,   *
 * and redistribution, is contained in the files COPYING and Copyright.html. *
 * COPYING can be found at the root of the source code distribution tree.    *
 * Or, see http://hdfgroup.org/products/hdf-java/doc/Copyright.html.         *
 * If you do not have access to either file, you may request a copy from     *
 * help@hdfgroup.org.                                                        *
 ****************************************************************************/

package ncsa.hdf.object.h5;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Vector;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.HDFNativeData;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.structs.H5O_info_t;
import ncsa.hdf.object.Attribute;
import ncsa.hdf.object.CompoundDS;
import ncsa.hdf.object.Dataset;
import ncsa.hdf.object.Datatype;
import ncsa.hdf.object.FileFormat;
import ncsa.hdf.object.Group;
import ncsa.hdf.object.HObject;

/**
 * The H5CompoundDS class defines an HDF5 dataset of compound datatypes.
 * <p>
 * An HDF5 dataset is an object composed of a collection of data elements, or
 * raw data, and metadata that stores a description of the data elements, data
 * layout, and all other information necessary to write, read, and interpret the
 * stored data.
 * <p>
 * A HDF5 compound datatype is similar to a struct in C or a common block in
 * Fortran: it is a collection of one or more atomic types or small arrays of
 * such types. Each member of a compound type has a name which is unique within
 * that type, and a byte offset that determines the first byte (smallest byte
 * address) of that member in a compound datum.
 * <p>
 * For more information on HDF5 datasets and datatypes, read the <a
 * href="http://hdfgroup.org/HDF5/doc/UG/index.html">HDF5 User's Guide</a>.
 * <p>
 * There are two basic types of compound datasets: simple compound data and
 * nested compound data. Members of a simple compound dataset have atomic
 * datatypes. Members of a nested compound dataset are compound or array of
 * compound data.
 * <p>
 * Since Java does not understand C structures, we cannot directly read/write
 * compound data values as in the following C example.
 * 
 * <pre>
 * typedef struct s1_t {
 *         int    a;
 *         float  b;
 *         double c; 
 *         } s1_t;
 *     s1_t       s1[LENGTH];
 *     ...
 *     H5Dwrite(..., s1);
 *     H5Dread(..., s1);
 * </pre>
 * 
 * Values of compound data fields are stored in java.util.Vector object. We read
 * and write compound data by fields instead of compound structure. As for the
 * example above, the java.util.Vector object has three elements: int[LENGTH],
 * float[LENGTH] and double[LENGTH]. Since Java understands the primitive
 * datatypes of int, float and double, we will be able to read/write the
 * compound data by field.
 * <p>
 * <p>
 * 
 * @version 1.1 9/4/2007
 * @author Peter X. Cao
 */
public class H5CompoundDS extends CompoundDS {
    /**
     * 
     */
    private static final long serialVersionUID = -5968625125574032736L;

    private final static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(H5CompoundDS.class);

    /**
     * The list of attributes attached data object.
     */
    private List<Attribute>   attributeList;

    private int               nAttributes      = -1;

    private H5O_info_t        obj_info;

    /**
     * A list of names of all fields including nested fields.
     * <p>
     * The nested names are separated by CompoundDs.separator. For example, if
     * compound dataset "A" has the following nested structure,
     * 
     * <pre>
     * A --> m01
     * A --> m02
     * A --> nest1 --> m11
     * A --> nest1 --> m12
     * A --> nest1 --> nest2 --> m21
     * A --> nest1 --> nest2 --> m22
     * i.e.
     * A = { m01, m02, nest1{m11, m12, nest2{ m21, m22}}}
     * </pre>
     * 
     * The flatNameList of compound dataset "A" will be {m01, m02, nest1[m11,
     * nest1[m12, nest1[nest2[m21, nest1[nest2[m22}
     * 
     */
    private List<String>      flatNameList;

    /**
     * A list of datatypes of all fields including nested fields.
     */
    private List<Integer>     flatTypeList;

    /** flag to indicate is the dataset is an external dataset */
    private boolean           isExternal       = false;

    /**
     * Constructs an HDF5 compound dataset with given file, dataset name and
     * path.
     * <p>
     * The dataset object represents an existing dataset in the file. For
     * example, new H5CompoundDS(file, "dset1", "/g0/") constructs a dataset
     * object that corresponds to the dataset,"dset1", at group "/g0/".
     * <p>
     * This object is usually constructed at FileFormat.open(), which loads the
     * file structure and object information into tree structure (TreeNode). It
     * is rarely used elsewhere.
     * <p>
     * 
     * @param theFile
     *            the file that contains the dataset.
     * @param name
     *            the name of the CompoundDS, e.g. "compDS".
     * @param path
     *            the path of the CompoundDS, e.g. "/g1".
     */
    public H5CompoundDS(FileFormat theFile, String name, String path) {
        this(theFile, name, path, null);
    }

    /**
     * @deprecated Not for public use in the future.<br>
     *             Using {@link #H5CompoundDS(FileFormat, String, String)}
     */
    @Deprecated
    public H5CompoundDS(FileFormat theFile, String name, String path, long[] oid) {
        super(theFile, name, path, oid);
        obj_info = new H5O_info_t(-1L, -1L, 0, 0, -1L, 0L, 0L, 0L, 0L, null, null, null);

        if ((oid == null) && (theFile != null)) {
            // retrieve the object ID
            try {
                byte[] ref_buf = H5.H5Rcreate(theFile.getFID(), this.getFullName(), HDF5Constants.H5R_OBJECT, -1);
                this.oid = new long[1];
                this.oid[0] = HDFNativeData.byteToLong(ref_buf, 0);
            }
            catch (Exception ex) {
            	log.debug("constructor ID {} for {} failed H5Rcreate", theFile.getFID(), this.getFullName());
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.DataFormat#hasAttribute()
     */
    public boolean hasAttribute() {
        obj_info.num_attrs = nAttributes;

        if (obj_info.num_attrs < 0) {
            int did = -1;
            try {
                did = H5.H5Dopen(getFID(), getPath() + getName(), HDF5Constants.H5P_DEFAULT);
                obj_info = H5.H5Oget_info(did);
                nAttributes = (int) obj_info.num_attrs;
            }
            catch (Exception ex) {
                obj_info.num_attrs = 0;
            }
            close(did);
        }

        return (obj_info.num_attrs > 0);
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.Dataset#getDatatype()
     */
    @Override
    public Datatype getDatatype() {
        if (datatype == null) {
            datatype = new H5Datatype(Datatype.CLASS_COMPOUND, -1, -1, -1);
        }

        return datatype;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.Dataset#clear()
     */
    @Override
    public void clear() {
        super.clear();

        if (attributeList != null) {
            ((Vector<Attribute>) attributeList).setSize(0);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.Dataset#readBytes()
     */
    @Override
    public byte[] readBytes() throws HDF5Exception {
        byte[] theData = null;

        if (rank <= 0) {
            init();
        }

        int did = open();
        if(did >= 0) {
	        int fspace = -1, mspace = -1, tid = -1;
	
	        try {
	            long[] lsize = { 1 };
	            for (int j = 0; j < selectedDims.length; j++) {
	                lsize[0] *= selectedDims[j];
	            }
	
	            fspace = H5.H5Dget_space(did);
	            mspace = H5.H5Screate_simple(rank, selectedDims, null);
	
	            // set the rectangle selection
	            // HDF5 bug: for scalar dataset, H5Sselect_hyperslab gives core dump
	            if (rank * dims[0] > 1) {
	                H5.H5Sselect_hyperslab(fspace, HDF5Constants.H5S_SELECT_SET, startDims, selectedStride, selectedDims,
	                        null); // set
	                // block
	                // to 1
	            }
	
	            tid = H5.H5Dget_type(did);
	            int size = H5.H5Tget_size(tid) * (int) lsize[0];
	            theData = new byte[size];
	            H5.H5Dread(did, tid, mspace, fspace, HDF5Constants.H5P_DEFAULT, theData);
	        }
	        finally {
	            try {
	                H5.H5Sclose(fspace);
	            }
	            catch (Exception ex2) {
	            	log.debug("finally close:", ex2);
	            }
	            try {
	                H5.H5Sclose(mspace);
	            }
	            catch (Exception ex2) {
	            	log.debug("finally close:", ex2);
	            }
	            try {
	                H5.H5Tclose(tid);
	            }
	            catch (HDF5Exception ex2) {
	            	log.debug("finally close:", ex2);
	            }
	            close(did);
	        }
        }

        return theData;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.Dataset#read()
     */
    @Override
    public Object read() throws HDF5Exception {
        List<Object> list = null;

        Object member_data = null;
        String member_name = null;
        int tid = -1, atom_tid = -1, member_class = -1, member_size = 0;
        int spaceIDs[] = { -1, -1 }; // spaceIDs[0]=mspace, spaceIDs[1]=fspace

        if (rank <= 0) {
            init(); // read data information into memory
        }

        if (numberOfMembers <= 0) {
            return null; // this compound dataset does not have any member
        }

        if (isExternal) {
            String pdir = this.getFileFormat().getAbsoluteFile().getParent();
            if (pdir == null) {
                pdir = ".";
            }
            H5.H5Dchdir_ext(pdir);
        }

        int did = open();
        if(did >= 0) {
        	list = new Vector<Object>(flatNameList.size());
        	Vector<Integer> atomicList = new Vector<Integer>();
        	try {
        		// to match finally for closing resources
        		long[] lsize = { 1 };
        		lsize[0] = selectHyperslab(did, spaceIDs);

        		// read each of member data into a byte array, then extract
        		// it into its type such, int, long, float, etc.
        		int n = flatNameList.size();
        		tid = H5.H5Dget_type(did);
        		int tclass = H5.H5Tget_class(tid);
        		if (tclass == HDF5Constants.H5T_ARRAY) {
        			// array of compound
        			int tmptid = tid;
        			tid = H5.H5Tget_super(tmptid);
        			try {
        				H5.H5Tclose(tmptid);
        			}
        			catch (HDF5Exception ex) {
        				log.debug("close array of compound:", ex);
        			}
        		}

        		extractCompoundInfo(tid, null, null, atomicList);

        		for (int i = 0; i < n; i++) {
        			boolean isVL = false;

        			if (!isMemberSelected[i]) {
        				log.debug("read: Member[{}] is not selected", i);
        				continue; // the field is not selected
        			}

        			member_name = new String(memberNames[i]);

        			atom_tid = ((Integer) atomicList.get(i)).intValue();

        			try {
        				member_class = H5.H5Tget_class(atom_tid);
        				member_size = H5.H5Tget_size(atom_tid);
        				member_data = H5Datatype.allocateArray(atom_tid, (int) lsize[0]);
        			}
        			catch (Exception ex) {
        				member_data = null;
        			}
    				log.debug("read: {} Member[{}] is class {} of size={}", member_name, i, member_class, member_size);

        			if (member_data == null || H5.H5Tequal(atom_tid, HDF5Constants.H5T_STD_REF_DSETREG)) {
        				String[] nullValues = new String[(int) lsize[0]];
        				String errorStr = "*unsupported*";
        				for (int j = 0; j < lsize[0]; j++) {
        					nullValues[j] = errorStr;
        				}
        				list.add(nullValues);
        				continue;
        			}

        			int comp_tid = -1;
        			int compInfo[] = { member_class, member_size, 0 };
        			try {
        				comp_tid = createCompoundFieldType(atom_tid, member_name, compInfo);
        				try {
        					// See BUG#951 isVL = H5.H5Tdetect_class(atom_tid,
        					// HDF5Constants.H5T_VLEN);
        					isVL = isVL || H5.H5Tis_variable_str(atom_tid);
        					isVL = isVL || H5.H5Tdetect_class(atom_tid, HDF5Constants.H5T_VLEN);
        				}
        				catch (Exception ex) {
        					log.debug("read: detection of varstr:", ex);
        				}

        				if (isVL) {
        					H5.H5DreadVL(did, comp_tid, spaceIDs[0], spaceIDs[1], HDF5Constants.H5P_DEFAULT,
        							(Object[]) member_data);
        				}
        				else {
        					H5.H5Dread(did, comp_tid, spaceIDs[0], spaceIDs[1], HDF5Constants.H5P_DEFAULT, member_data);
        				}
        			}
        			catch (HDF5Exception ex2) {
        				String[] nullValues = new String[(int) lsize[0]];
        				for (int j = 0; j < lsize[0]; j++) {
        					nullValues[j] = "";
        				}
        				list.add(nullValues);
    					log.debug("read: {} Member[{}] createCompoundFieldType and read failure:", member_name, i, ex2);
        				continue;
        			}
        			finally {
        				try {
        					H5.H5Tclose(comp_tid);
        				}
        				catch (Exception ex3) {
        					log.debug("read: finally close:", ex3);
        				}
        			}

        			if (!isVL) {
        				String cname = member_data.getClass().getName();
        				char dname = cname.charAt(cname.lastIndexOf("[") + 1);

        				if ((member_class == HDF5Constants.H5T_STRING) && convertByteToString) {
        					if (dname == 'B')
        						member_data = byteToString((byte[]) member_data, member_size / memberOrders[i]);
        				}
        				else if (member_class == HDF5Constants.H5T_REFERENCE) {
        					if (dname == 'B') member_data = HDFNativeData.byteToLong((byte[]) member_data);
        				}
        				else if (compInfo[2] != 0) {
        					member_data = Dataset.convertFromUnsignedC(member_data, null);
        				}
        				else if (member_class == HDF5Constants.H5T_ENUM && enumConverted) {
        					try {
        						String[] strs = H5Datatype.convertEnumValueToName(atom_tid, member_data, null);
        						if (strs != null) {
        							member_data = strs;
        						}
        					}
        					catch (Exception ex) {
        						log.debug("read: H5Datatype.convertEnumValueToName:", ex);
        					}
        				}
        			}

        			list.add(member_data);
        		} // end of for (int i=0; i<num_members; i++)

        	}
        	finally {
        		try {
        			if(HDF5Constants.H5S_ALL != spaceIDs[0])
        				H5.H5Sclose(spaceIDs[0]);
        		}
        		catch (Exception ex2) {
        			log.debug("read: finally close:", ex2);
        		}
        		try {
        			if(HDF5Constants.H5S_ALL != spaceIDs[1])
        				H5.H5Sclose(spaceIDs[1]);
        		}
        		catch (Exception ex2) {
        			log.debug("read: finally close:", ex2);
        		}
        		try {
        			H5.H5Tclose(tid);
        		}
        		catch (Exception ex2) {
        			log.debug("read: finally close:", ex2);
        		}

        		// close atomic types
        		int ntypes = atomicList.size();
        		for (int i = 0; i < ntypes; i++) {
        			atom_tid = ((Integer) atomicList.get(i)).intValue();
        			try {
        				H5.H5Tclose(atom_tid);
        			}
        			catch (Exception ex2) {
        				log.debug("finally close:", ex2);
        			}
        		}

        		close(did);
        	}
        }

        return list;
    }

    /**
     * Writes the given data buffer into this dataset in a file.
     * <p>
     * The data buffer is a vector that contains the data values of compound
     * fields. The data is written into file field by field.
     * 
     * @param buf
     *            The vector that contains the data values of compound fields.
     */
    @Override
    public void write(Object buf) throws HDF5Exception {
        if ((buf == null) || (numberOfMembers <= 0) || !(buf instanceof List)) {
            return;
        }

        List<?> list = (List<?>) buf;

        Object member_data = null;
        String member_name = null;
        int tid = -1, atom_tid = -1, member_class = -1, member_size = 0;
        int spaceIDs[] = { -1, -1 }; // spaceIDs[0]=mspace, spaceIDs[1]=fspace

        int did = open();
        if(did >= 0) {
        	Vector<Integer> atomicList = new Vector<Integer>();
        	try { // to match finally for closing resources
        		long[] lsize = { 1 };
        		lsize[0] = selectHyperslab(did, spaceIDs);

        		// read each of member data into a byte array, then extract
        		// it into its type such, int, long, float, etc.
        		int idx = 0;
        		int n = flatNameList.size();
        		boolean isEnum = false;
        		tid = H5.H5Dget_type(did);

        		extractCompoundInfo(tid, null, null, atomicList);
        		for (int i = 0; i < n; i++) {
        			if (!isMemberSelected[i]) {
        				log.debug("write: Member[{}] is not selected", i);
        				continue; // the field is not selected
        			}

        			member_name = new String(memberNames[i]);
        			atom_tid = ((Integer) atomicList.get(i)).intValue();
        			member_data = list.get(idx++);

        			if (member_data == null) {
        				log.debug("write: Member[{}] data is null", i);
        				continue;
        			}

        			boolean isVL = false;
        			try {
        				isVL = (H5.H5Tdetect_class(atom_tid, HDF5Constants.H5T_VLEN));
        			}
        			catch (Exception ex) {
        				log.debug("isVL:", ex);
        			}

        			if (isVL) {
        				log.debug("write: Member[{}] is VL", i);
        				continue;
        			}

        			try {
        				member_class = H5.H5Tget_class(atom_tid);
        				member_size = H5.H5Tget_size(atom_tid);
        				isEnum = (member_class == HDF5Constants.H5T_ENUM);
        			}
        			catch (Exception ex) {
        				log.debug("write: member class - size:", ex);
        			}
    				log.debug("write: {} Member[{}] is class {} of size={}", member_name, i, member_class, member_size);

        			Object tmpData = member_data;

        			int comp_tid = -1;
        			int compInfo[] = { member_class, member_size, 0 };
        			try {
        				comp_tid = createCompoundFieldType(atom_tid, member_name, compInfo);
        				log.debug("write: {} Member[{}] compInfo[class]={} compInfo[size]={} compInfo[unsigned]={}", member_name, i, compInfo[0], compInfo[1], compInfo[2]);
        				if (compInfo[2] != 0) {
        	        		// check if need to convert integer data
        	        		int tsize = H5.H5Tget_size(comp_tid);
        	        		String cname = member_data.getClass().getName();
        	        		char dname = cname.charAt(cname.lastIndexOf("[") + 1);
        	        		boolean doConversion = (((tsize == 1) && (dname == 'S')) || ((tsize == 2) && (dname == 'I'))
        	        				|| ((tsize == 4) && (dname == 'J')));

        	        		tmpData = member_data;
        	        		if (doConversion) {
        	        			tmpData = convertToUnsignedC(member_data, null);
        	        		}
            				log.debug("write: {} Member[{}] convertToUnsignedC", member_name, i);
        				}
        				else if ((member_class == HDF5Constants.H5T_STRING)
        						&& (Array.get(member_data, 0) instanceof String)) {
        					tmpData = stringToByte((String[]) member_data, member_size);
            				log.debug("write: {} Member[{}] stringToByte", member_name, i);
        				}
        				else if (isEnum && (Array.get(member_data, 0) instanceof String)) {
        					tmpData = H5Datatype.convertEnumNameToValue(atom_tid, (String[]) member_data, null);
            				log.debug("write: {} Member[{}] convertEnumNameToValue", member_name, i);
        				}

        				if (tmpData != null) {
        					// BUG!!! does not write nested compound data and no
        					// exception was caught
        					// need to check if it is a java error or C library
        					// error
        					H5.H5Dwrite(did, comp_tid, spaceIDs[0], spaceIDs[1], HDF5Constants.H5P_DEFAULT, tmpData);
            			}
        			}
            		catch (Exception ex1) {
            			log.debug("write: H5Dwrite process failure:", ex1);
            		}
        			finally {
        				try {
        					H5.H5Tclose(comp_tid);
        				}
        				catch (Exception ex2) {
        					log.debug("write: finally close:", ex2);
        				}
        			}
        		} // end of for (int i=0; i<num_members; i++)
        	}
        	finally {
        		try {
        			if(HDF5Constants.H5S_ALL != spaceIDs[0])
        				H5.H5Sclose(spaceIDs[0]);
        		}
        		catch (Exception ex2) {
        			log.debug("write: finally close:", ex2);
        		}
        		try {
        			if(HDF5Constants.H5S_ALL != spaceIDs[1])
        				H5.H5Sclose(spaceIDs[1]);
        		}
        		catch (Exception ex2) {
        			log.debug("write: finally close:", ex2);
        		}
        		try {
        			H5.H5Tclose(tid);
        		}
        		catch (Exception ex2) {
        			log.debug("write: finally close:", ex2);
        		}

        		// close atomic types
        		int ntypes = atomicList.size();
        		for (int i = 0; i < ntypes; i++) {
        			atom_tid = ((Integer) atomicList.get(i)).intValue();
        			try {
        				H5.H5Tclose(atom_tid);
        			}
        			catch (Exception ex2) {
        				log.debug("write: finally close:", ex2);
        			}
        		}
        	}

        	close(did);
        }
    }

    /**
     * Set up the selection of hyperslab
     * 
     * @param did
     *            IN dataset ID
     * @param spaceIDs
     *            IN/OUT memory and file space IDs -- spaceIDs[0]=mspace,
     *            spaceIDs[1]=fspace
     * @return total number of data point selected
     */
    private long selectHyperslab(int did, int[] spaceIDs) throws HDF5Exception {
        long lsize = 1;

        boolean isAllSelected = true;
        for (int i = 0; i < rank; i++) {
            lsize *= selectedDims[i];
            if (selectedDims[i] < dims[i]) {
                isAllSelected = false;
            }
        }

        if (isAllSelected) {
            spaceIDs[0] = HDF5Constants.H5S_ALL;
            spaceIDs[1] = HDF5Constants.H5S_ALL;
        }
        else {
            spaceIDs[1] = H5.H5Dget_space(did);

            // When 1D dataspace is used in chunked dataset, reading is very
            // slow.
            // It is a known problem on HDF5 library for chunked dataset.
            // mspace = H5.H5Screate_simple(1, lsize, null);
            spaceIDs[0] = H5.H5Screate_simple(rank, selectedDims, null);
            H5.H5Sselect_hyperslab(spaceIDs[1], HDF5Constants.H5S_SELECT_SET, startDims, selectedStride, selectedDims,
                    null);
        }

        return lsize;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.DataFormat#getMetadata()
     */
    public List<Attribute> getMetadata() throws HDF5Exception {
        return this.getMetadata(fileFormat.getIndexType(null), fileFormat.getIndexOrder(null));
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.DataFormat#getMetadata(int...)
     */
    public List<Attribute> getMetadata(int... attrPropList) throws HDF5Exception {

        if (rank <= 0) {
            init();
        }
        try {
            this.linkTargetObjName = H5File.getLinkTargetName(this);
        }
        catch (Exception ex) {
        	log.debug("linkTargetObjName:", ex);
        }

        if (attributeList != null) {
            return attributeList;
        }

        // load attributes first
        int did = -1, pid = -1;
        int indxType = fileFormat.getIndexType(null);
        int order = fileFormat.getIndexOrder(null);

        if (attrPropList.length > 0) {
            indxType = attrPropList[0];
            if (attrPropList.length > 1) {
                order = attrPropList[1];
            }
        }
        
        did = open();
        if(did >= 0) {
        	try {
        		attributeList = H5File.getAttribute(did, indxType, order);

        		// get the compression and chunk information
        		pid = H5.H5Dget_create_plist(did);
        		if (H5.H5Pget_layout(pid) == HDF5Constants.H5D_CHUNKED) {
        			chunkSize = new long[rank];
        			H5.H5Pget_chunk(pid, rank, chunkSize);
        		}
        		else {
        			chunkSize = null;
        		}

        		int[] flags = { 0, 0 };
        		long[] cd_nelmts = { 2 };
        		int[] cd_values = { 0, 0 };
        		String[] cd_name = { "", "" };
        		int nfilt = H5.H5Pget_nfilters(pid);
        		int filter = -1;
        		int[] filter_config = { 1 };
        		compression = "";

        		for (int i = 0; i < nfilt; i++) {
        			if (i > 0) {
        				compression += ", ";
        			}

        			try {
        				filter = H5.H5Pget_filter(pid, i, flags, cd_nelmts, cd_values, 120, cd_name, filter_config);
        			}
        			catch (Throwable err) {
        				compression += "ERROR";
        				continue;
        			}

        			if (filter == HDF5Constants.H5Z_FILTER_DEFLATE) {
        				compression += "GZIP: level = " + cd_values[0];
        			}
        			else if (filter == HDF5Constants.H5Z_FILTER_FLETCHER32) {
        				compression += "Error detection filter";
        			}
        			else if (filter == HDF5Constants.H5Z_FILTER_SHUFFLE) {
        				compression += "SHUFFLE: Nbytes = " + cd_values[0];
        			}
        			else if (filter == HDF5Constants.H5Z_FILTER_SZIP) {
        				compression += "SZIP: Pixels per block = " + cd_values[1];
        				int flag = -1;
        				try {
        					flag = H5.H5Zget_filter_info(filter);
        				}
        				catch (Exception ex) {
        					flag = -1;
        				}
        				if (flag == HDF5Constants.H5Z_FILTER_CONFIG_DECODE_ENABLED) {
        					compression += ": H5Z_FILTER_CONFIG_DECODE_ENABLED";
        				}
        				else if ((flag == HDF5Constants.H5Z_FILTER_CONFIG_ENCODE_ENABLED)
        						|| (flag >= (HDF5Constants.H5Z_FILTER_CONFIG_ENCODE_ENABLED + HDF5Constants.H5Z_FILTER_CONFIG_DECODE_ENABLED))) {
        					compression += ": H5Z_FILTER_CONFIG_ENCODE_ENABLED";
        				}
        			}
        		} // for (int i=0; i<nfilt; i++)

        		if (compression.length() == 0) {
        			compression = "NONE";
        		}

        		try {
        			int[] at = { 0 };
        			H5.H5Pget_alloc_time(pid, at);
        			compression += ",         Storage allocation time: ";
        			if (at[0] == HDF5Constants.H5D_ALLOC_TIME_EARLY) {
        				compression += "Early";
        			}
        			else if (at[0] == HDF5Constants.H5D_ALLOC_TIME_INCR) {
        				compression += "Incremental";
        			}
        			else if (at[0] == HDF5Constants.H5D_ALLOC_TIME_LATE) {
        				compression += "Late";
        			}
        		}
        		catch (Exception ex) {
        			log.debug("Storage allocation time:", ex);
        		}
        	}
        	finally {
        		try {
        			H5.H5Pclose(pid);
        		}
        		catch (Exception ex) {
        			log.debug("finally close:", ex);
        		}

        		close(did);
        	}
        }

        return attributeList;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.DataFormat#writeMetadata(java.lang.Object)
     */
    public void writeMetadata(Object info) throws Exception {
        // only attribute metadata is supported.
        if (!(info instanceof Attribute)) {
            return;
        }

        boolean attrExisted = false;
        Attribute attr = (Attribute) info;

        if (attributeList == null) {
            this.getMetadata();
        }

        if (attributeList != null) attrExisted = attributeList.contains(attr);

        getFileFormat().writeAttribute(this, attr, attrExisted);
        // add the new attribute into attribute list
        if (!attrExisted) {
            attributeList.add(attr);
            nAttributes = attributeList.size();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.DataFormat#removeMetadata(java.lang.Object)
     */
    public void removeMetadata(Object info) throws HDF5Exception {
        // only attribute metadata is supported.
        if (!(info instanceof Attribute)) {
            return;
        }

        Attribute attr = (Attribute) info;
        int did = open();
        if(did >= 0) {
	        try {
	            H5.H5Adelete(did, attr.getName());
	            List<Attribute> attrList = getMetadata();
	            attrList.remove(attr);
	            nAttributes = attrList.size();
	        }
	        finally {
	            close(did);
	        }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.HObject#open()
     */
    @Override
    public int open() {
        int did = -1;

        try {
            did = H5.H5Dopen(getFID(), getPath() + getName(), HDF5Constants.H5P_DEFAULT);
        }
        catch (HDF5Exception ex) {
        	log.debug("Failed to open dataset {}", getPath() + getName());
            did = -1;
        }

        return did;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.HObject#close(int)
     */
    @Override
    public void close(int did) {
    	if(did >= 0) {
	        try {
	            H5.H5Fflush(did, HDF5Constants.H5F_SCOPE_LOCAL);
	        }
	        catch (Exception ex) {
	        	log.debug("close.H5Fflush:", ex);
	        }
	        try {
	            H5.H5Dclose(did);
	        }
	        catch (HDF5Exception ex) {
	        	log.debug("close.H5Dclose:", ex);
	        }
    	}
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.Dataset#init()
     */
    @Override
    public void init() {
        if (rank > 0) {
            resetSelection();
            return; // already called. Initialize only once
        }

        int did = -1, sid = -1, tid = -1, tclass = -1;
        flatNameList = new Vector<String>();
        flatTypeList = new Vector<Integer>();
        int[] memberTIDs = null;

        did = open();

        if(did >= 0) {
        	// check if it is an external dataset
        	int pid = -1;
        	try {
        		pid = H5.H5Dget_create_plist(did);
        		int nfiles = H5.H5Pget_external_count(pid);
        		isExternal = (nfiles > 0);
        	}
        	catch (Exception ex) {
        		log.debug("check if it is an external dataset:", ex);
        	}
        	finally {
        		try {
        			H5.H5Pclose(pid);
        		}
        		catch (Exception ex) {
        			log.debug("finally close:", ex);
        		}
        	}

        	try {
        		sid = H5.H5Dget_space(did);
        		rank = H5.H5Sget_simple_extent_ndims(sid);

        		if (rank == 0) {
        			// a scalar data point
        			rank = 1;
        			dims = new long[1];
        			dims[0] = 1;
        		}
        		else {
        			dims = new long[rank];
        			maxDims = new long[rank];
        			H5.H5Sget_simple_extent_dims(sid, dims, maxDims);
        		}

        		startDims = new long[rank];
        		selectedDims = new long[rank];

        		// initialize member information
        		tid = H5.H5Dget_type(did);
        		tclass = H5.H5Tget_class(tid);

        		int tmptid = 0;
        		if (tclass == HDF5Constants.H5T_ARRAY) {
        			// array of compound
        			tmptid = tid;
        			tid = H5.H5Tget_super(tmptid);
        			try {
        				H5.H5Tclose(tmptid);
        			}
        			catch (HDF5Exception ex) {
        				log.debug("array of compound:", ex);
        			}
        		}

        		extractCompoundInfo(tid, "", flatNameList, flatTypeList);
        		numberOfMembers = flatNameList.size();

        		memberNames = new String[numberOfMembers];
        		memberTIDs = new int[numberOfMembers];
        		memberTypes = new Datatype[numberOfMembers];
        		memberOrders = new int[numberOfMembers];
        		isMemberSelected = new boolean[numberOfMembers];
        		memberDims = new Object[numberOfMembers];

        		for (int i = 0; i < numberOfMembers; i++) {
        			isMemberSelected[i] = true;
        			memberTIDs[i] = ((Integer) flatTypeList.get(i)).intValue();
        			memberTypes[i] = new H5Datatype(memberTIDs[i]);
        			memberNames[i] = (String) flatNameList.get(i);
        			memberOrders[i] = 1;
        			memberDims[i] = null;

        			try {
        				tclass = H5.H5Tget_class(memberTIDs[i]);
        			}
        			catch (HDF5Exception ex) {
        				log.debug("memberTIDs[{}]:", i, ex);
        			}

        			if (tclass == HDF5Constants.H5T_ARRAY) {
        				int n = H5.H5Tget_array_ndims(memberTIDs[i]);
        				long mdim[] = new long[n];
        				H5.H5Tget_array_dims(memberTIDs[i], mdim);
        				int idim[] = new int[n];
        				for (int j = 0; j < n; j++)
        					idim[j] = (int) mdim[j];
        				memberDims[i] = idim;
        				tmptid = H5.H5Tget_super(memberTIDs[i]);
        				memberOrders[i] = (H5.H5Tget_size(memberTIDs[i]) / H5.H5Tget_size(tmptid));
        				try {
        					H5.H5Tclose(tmptid);
        				}
        				catch (HDF5Exception ex) {
        					log.debug("close temp of memberTIDs[{}]:", i, ex);
        				}
        			}
        		} // for (int i=0; i<numberOfMembers; i++)
        	}
        	catch (HDF5Exception ex) {
        		numberOfMembers = 0;
        		memberNames = null;
        		memberTypes = null;
        		memberOrders = null;
        	}
        	finally {
        		try {
        			H5.H5Tclose(tid);
        		}
        		catch (HDF5Exception ex2) {
        			log.debug("finally close:", ex2);
        		}
        		try {
        			H5.H5Sclose(sid);
        		}
        		catch (HDF5Exception ex2) {
        			log.debug("finally close:", ex2);
        		}

        		if (memberTIDs != null) {
        			for (int i = 0; i < memberTIDs.length; i++) {
        				try {
        					H5.H5Tclose(memberTIDs[i]);
        				}
        				catch (Exception ex) {
        					log.debug("finally close:", ex);
        				}
        			}
        		}
        	}

        	close(did);
        }

        resetSelection();
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.HObject#setName(java.lang.String)
     */
    @Override
    public void setName(String newName) throws Exception {
        H5File.renameObject(this, newName);
        super.setName(newName);
    }

    /**
     * Resets selection of dataspace
     */
    private void resetSelection() {

        for (int i = 0; i < rank; i++) {
            startDims[i] = 0;
            selectedDims[i] = 1;
            if (selectedStride != null) {
                selectedStride[i] = 1;
            }
        }

        if (rank == 1) {
            selectedIndex[0] = 0;
            selectedDims[0] = dims[0];
        }
        else if (rank == 2) {
            selectedIndex[0] = 0;
            selectedIndex[1] = 1;
            selectedDims[0] = dims[0];
            selectedDims[1] = dims[1];
        }
        else if (rank > 2) {
            selectedIndex[0] = rank - 2; // columns
            selectedIndex[1] = rank - 1; // rows
            selectedIndex[2] = rank - 3;
            selectedDims[rank - 1] = dims[rank - 1];
            selectedDims[rank - 2] = dims[rank - 2];
        }

        isDataLoaded = false;
        setMemberSelection(true);
    }

    /**
     * Extracts compound information into flat structure.
     * <p>
     * For example, compound datatype "nest" has {nest1{a, b, c}, d, e} then
     * extractCompoundInfo() will put the names of nested compound fields into a
     * flat list as
     * 
     * <pre>
     * nest.nest1.a
     * nest.nest1.b
     * nest.nest1.c
     * nest.d
     * nest.e
     * </pre>
     */
    private void extractCompoundInfo(int tid, String name, List<String> names, List<Integer> types) {
        int nMembers = 0, mclass = -1, mtype = -1;
        String mname = null;

        try {
            nMembers = H5.H5Tget_nmembers(tid);
        }
        catch (Exception ex) {
            nMembers = 0;
        }

        if (nMembers <= 0) {
            return;
        }

        int tmptid = -1;
        for (int i = 0; i < nMembers; i++) {

            try {
                mtype = H5.H5Tget_member_type(tid, i);
            }
            catch (Exception ex) {
            	log.debug("continue H5Tget_member_type[{}]:", i, ex);
                continue;
            }

            try {
                tmptid = mtype;
                mtype = H5.H5Tget_native_type(tmptid);
            }
            catch (HDF5Exception ex) {
            	log.debug("continue H5Tget_native_type[{}]:", i, ex);
                continue;
            }
            finally {
                try {
                    H5.H5Tclose(tmptid);
                }
                catch (HDF5Exception ex) {
                	log.debug("finally close:", ex);
                }
            }

            try {
                mclass = H5.H5Tget_class(mtype);
            }
            catch (HDF5Exception ex) {
            	log.debug("continue H5Tget_class[{}]:", i, ex);
                continue;
            }

            if (names != null) {
                mname = name + H5.H5Tget_member_name(tid, i);
            }

            if (mclass == HDF5Constants.H5T_COMPOUND) {
            	log.debug("continue after recursive H5T_COMPOUND[{}]:", i);
                extractCompoundInfo(mtype, mname + CompoundDS.separator, names, types);
                continue;
            }
            else if (mclass == HDF5Constants.H5T_ARRAY) {
                try {
                    tmptid = H5.H5Tget_super(mtype);
                    int tmpclass = H5.H5Tget_class(tmptid);

                    // cannot deal with ARRAY of COMPOUND or ARRAY of ARRAY
                    // support only ARRAY of atomic types
                    if ((tmpclass == HDF5Constants.H5T_COMPOUND) || (tmpclass == HDF5Constants.H5T_ARRAY)) {
                    	log.debug("continue unsupported ARRAY of COMPOUND or ARRAY of ARRAY[{}]:", i);
                        continue;
                    }
                }
                catch (Exception ex) {
                	log.debug("continue H5T_ARRAY id or class failure[{}]:", i, ex);
                    continue;
                }
                finally {
                    try {
                        H5.H5Tclose(tmptid);
                    }
                    catch (Exception ex) {
                    	log.debug("finally close[{}]:", i, ex);
                    }
                }
            }

            if (names != null) {
                names.add(mname);
            }
            types.add(new Integer(mtype));

        } // for (int i=0; i<nMembers; i++)
    } // extractNestedCompoundInfo

    /**
     * @deprecated Not for public use in the future. <br>
     *             Using
     *             {@link #create(String, Group, long[], long[], long[], int, String[], Datatype[], int[], long[][], Object)}
     */
    @Deprecated
    public static Dataset create(String name, Group pgroup, long[] dims, String[] memberNames,
            Datatype[] memberDatatypes, int[] memberSizes, Object data) throws Exception {
        if ((pgroup == null) || (name == null) || (dims == null) || (memberNames == null) || (memberDatatypes == null)
                || (memberSizes == null)) {
            return null;
        }

        int nMembers = memberNames.length;
        int memberRanks[] = new int[nMembers];
        long memberDims[][] = new long[nMembers][1];
        for (int i = 0; i < nMembers; i++) {
            memberRanks[i] = 1;
            memberDims[i][0] = memberSizes[i];
        }

        return H5CompoundDS.create(name, pgroup, dims, memberNames, memberDatatypes, memberRanks, memberDims, data);
    }

    /**
     * @deprecated Not for public use in the future. <br>
     *             Using
     *             {@link #create(String, Group, long[], long[], long[], int, String[], Datatype[], int[], long[][], Object)}
     */
    @Deprecated
    public static Dataset create(String name, Group pgroup, long[] dims, String[] memberNames,
            Datatype[] memberDatatypes, int[] memberRanks, long[][] memberDims, Object data) throws Exception {
        return H5CompoundDS.create(name, pgroup, dims, null, null, -1, memberNames, memberDatatypes, memberRanks,
                memberDims, data);
    }

    /**
     * Creates a simple compound dataset in a file with/without chunking and
     * compression
     * <p>
     * This function provides an easy way to create a simple compound dataset in
     * file by hiding tedious details of creating a compound dataset from users.
     * <p>
     * This function calls H5.H5Dcreate() to create a simple compound dataset in
     * file. Nested compound dataset is not supported. The required information
     * to create a compound dataset includes the name, the parent group and data
     * space of the dataset, the names, datatypes and data spaces of the
     * compound fields. Other information such as chunks, compression and the
     * data buffer is optional.
     * <p>
     * The following example shows how to use this function to create a compound
     * dataset in file.
     * 
     * <pre>
     * H5File file = null;
     * String message = &quot;&quot;;
     * Group pgroup = null;
     * int[] DATA_INT = new int[DIM_SIZE];
     * float[] DATA_FLOAT = new float[DIM_SIZE];
     * String[] DATA_STR = new String[DIM_SIZE];
     * long[] DIMs = { 50, 10 };
     * long[] CHUNKs = { 25, 5 };
     * 
     * try {
     *     file = (H5File) H5FILE.open(fname, H5File.CREATE);
     *     file.open();
     *     pgroup = (Group) file.get(&quot;/&quot;);
     * }
     * catch (Exception ex) {}
     * 
     * Vector data = new Vector();
     * data.add(0, DATA_INT);
     * data.add(1, DATA_FLOAT);
     * data.add(2, DATA_STR);
     * 
     * // create groups
     * Datatype[] mdtypes = new H5Datatype[3];
     * String[] mnames = { &quot;int&quot;, &quot;float&quot;, &quot;string&quot; };
     * Dataset dset = null;
     * try {
     *     mdtypes[0] = new H5Datatype(Datatype.CLASS_INTEGER, 4, -1, -1);
     *     mdtypes[1] = new H5Datatype(Datatype.CLASS_FLOAT, 4, -1, -1);
     *     mdtypes[2] = new H5Datatype(Datatype.CLASS_STRING, STR_LEN, -1, -1);
     *     dset = file.createCompoundDS(&quot;/CompoundDS&quot;, pgroup, DIMs, null, CHUNKs, 9, mnames, mdtypes, null, data);
     * }
     * catch (Exception ex) {
     *     failed(message, ex, file);
     *     return 1;
     * }
     * </pre>
     * 
     * @param name
     *            the name of the new dataset
     * @param pgroup
     *            parent group where the new dataset is created.
     * @param dims
     *            the dimension size
     * @param maxdims
     *            maximum dimension sizes of the new dataset, null if maxdims is
     *            the same as dims.
     * @param chunks
     *            chunk sizes of the new dataset, null if no chunking
     * @param gzip
     *            GZIP compression level (1 to 9), 0 or negative values if no
     *            compression.
     * @param memberNames
     *            the names of compound datatype
     * @param memberDatatypes
     *            the datatypes of the compound datatype
     * @param memberRanks
     *            the ranks of the members
     * @param memberDims
     *            the dim sizes of the members
     * @param data
     *            list of data arrays written to the new dataset, null if no
     *            data is written to the new dataset.
     * 
     * @return the new compound dataset if successful; otherwise returns null
     */
    public static Dataset create(String name, Group pgroup, long[] dims, long[] maxdims, long[] chunks, int gzip,
            String[] memberNames, Datatype[] memberDatatypes, int[] memberRanks, long[][] memberDims, Object data)
                    throws Exception {
        H5CompoundDS dataset = null;
        String fullPath = null;
        int did = -1, sid = -1, tid = -1, plist = -1;

        if ((pgroup == null) || (name == null) || (dims == null) || (memberNames == null) || (memberDatatypes == null)
                || (memberRanks == null) || (memberDims == null)) {
            return null;
        }

        H5File file = (H5File) pgroup.getFileFormat();
        if (file == null) {
            return null;
        }

        String path = HObject.separator;
        if (!pgroup.isRoot()) {
            path = pgroup.getPath() + pgroup.getName() + HObject.separator;
            if (name.endsWith("/")) {
                name = name.substring(0, name.length() - 1);
            }
            int idx = name.lastIndexOf("/");
            if (idx >= 0) {
                name = name.substring(idx + 1);
            }
        }

        fullPath = path + name;

        int typeSize = 0;
        int nMembers = memberNames.length;
        int[] mTypes = new int[nMembers];
        int memberSize = 1;
        int rank = dims.length;
        for (int i = 0; i < nMembers; i++) {
            memberSize = 1;
            for (int j = 0; j < memberRanks[i]; j++) {
                memberSize *= memberDims[i][j];
            }

            mTypes[i] = -1;
            // the member is an array
            if ((memberSize > 1) && (memberDatatypes[i].getDatatypeClass() != Datatype.CLASS_STRING)) {
                int tmptid = -1;
                if((tmptid = memberDatatypes[i].toNative()) >= 0) {
	                try {
	                    mTypes[i] = H5.H5Tarray_create(tmptid, memberRanks[i], memberDims[i]);
	                }
	                finally {
	                    try {
	                        H5.H5Tclose(tmptid);
	                    }
	                    catch (Exception ex) {
	                    	log.debug("compound array create finally close:", ex);
	                    }
	                }
                }
            }
            else {
                mTypes[i] = memberDatatypes[i].toNative();
            }
            try {
            	typeSize += H5.H5Tget_size(mTypes[i]);
            }
            catch (Exception ex) {
            	log.debug("array create H5Tget_size:", ex);

                while (i > 0) {
                    try {
                        H5.H5Tclose(mTypes[i]);
                    }
                    catch (HDF5Exception ex2) {
                    	log.debug("compound create finally close:", ex2);
                    }
                    i--;
                }
                throw ex;
            }
        }

        try {
            tid = H5.H5Tcreate(HDF5Constants.H5T_COMPOUND, typeSize);
            int offset = 0;
            for (int i = 0; i < nMembers; i++) {
                H5.H5Tinsert(tid, memberNames[i], offset, mTypes[i]);
                offset += H5.H5Tget_size(mTypes[i]);
            }

            sid = H5.H5Screate_simple(rank, dims, maxdims);

            // setup chunking and compression
            boolean isExtentable = false;
            if (maxdims != null) {
                for (int i = 0; i < maxdims.length; i++) {
                    if (maxdims[i] == 0) {
                        maxdims[i] = dims[i];
                    }
                    else if (maxdims[i] < 0) {
                        maxdims[i] = HDF5Constants.H5S_UNLIMITED;
                    }

                    if (maxdims[i] != dims[i]) {
                        isExtentable = true;
                    }
                }
            }
            // HDF 5 requires you to use chunking in order to define extendible
            // datasets. Chunking makes it possible to extend datasets
            // efficiently,
            // without having to reorganize storage excessively
            if ((chunks == null) && isExtentable) {
                chunks = dims;
            }

            plist = HDF5Constants.H5P_DEFAULT;
            if (chunks != null) {
                plist = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE);
                H5.H5Pset_layout(plist, HDF5Constants.H5D_CHUNKED);
                H5.H5Pset_chunk(plist, rank, chunks);

                // compression requires chunking
                if (gzip > 0) {
                    H5.H5Pset_deflate(plist, gzip);
                }
            }

            int fid = file.getFID();
            did = H5.H5Dcreate(fid, fullPath, tid, sid, HDF5Constants.H5P_DEFAULT, plist, HDF5Constants.H5P_DEFAULT);
        }
        finally {
            try {
                H5.H5Pclose(plist);
            }
            catch (HDF5Exception ex) {
            	log.debug("compound create finally close:", ex);
            }
            try {
                H5.H5Sclose(sid);
            }
            catch (HDF5Exception ex) {
            	log.debug("compound create finally close:", ex);
            }
            try {
                H5.H5Tclose(tid);
            }
            catch (HDF5Exception ex) {
            	log.debug("compound create finally close:", ex);
            }
            try {
                H5.H5Dclose(did);
            }
            catch (HDF5Exception ex) {
            	log.debug("compound create finally close:", ex);
            }

            for (int i = 0; i < nMembers; i++) {
                try {
                    H5.H5Tclose(mTypes[i]);
                }
                catch (HDF5Exception ex) {
                	log.debug("compound create finally close:", ex);
                }
            }
        }

        dataset = new H5CompoundDS(file, name, path);

        if (dataset != null) {
            pgroup.addToMemberList(dataset);

            if (data != null) {
                dataset.init();
                long selected[] = dataset.getSelectedDims();
                for (int i = 0; i < rank; i++) {
                    selected[i] = dims[i];
                }
                dataset.write(data);
            }
        }

        return dataset;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.Dataset#isString(int)
     */
    @Override
    public boolean isString(int tid) {
        boolean b = false;
        try {
            b = (HDF5Constants.H5T_STRING == H5.H5Tget_class(tid));
        }
        catch (Exception ex) {
            b = false;
        }

        return b;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.Dataset#getSize(int)
     */
    @Override
    public int getSize(int tid) {
        int tsize = -1;

        try {
            tsize = H5.H5Tget_size(tid);
        }
        catch (Exception ex) {
            tsize = -1;
        }

        return tsize;
    }

    /**
     * Creates a datatype of a compound with one field.
     * <p>
     * This function is needed to read/write data field by field.
     * <p>
     * 
     * @param member_tid
     *            The datatype identifier of the compound to create
     * @param member_name
     *            The name of the datatype
     * @param compInfo
     *            compInfo[0]--IN: class of member datatype; compInfo[1]--IN:
     *            size of member datatype; compInfo[2]--OUT: non-zero if the
     *            base type of the compound field is unsigned; zero, otherwise.
     * @return the identifier of the compound datatype.
     */
    private final int createCompoundFieldType(int member_tid, String member_name, int[] compInfo) throws HDF5Exception {
        int nested_tid = -1;

        int arrayType = member_tid;
        int baseType = arrayType;
        int tmp_tid1 = -1, tmp_tid4 = -1;

        try {
            int member_class = compInfo[0];
            int member_size = compInfo[1];

			log.debug("{} Member is class {} of size={} with baseType={}", member_name, member_class, member_size, baseType);
            if (member_class == HDF5Constants.H5T_ARRAY) {
                int mn = H5.H5Tget_array_ndims(member_tid);
                long[] marray = new long[mn];
                H5.H5Tget_array_dims(member_tid, marray);
                baseType = H5.H5Tget_super(member_tid);
                tmp_tid4 = H5.H5Tget_native_type(baseType);
                arrayType = H5.H5Tarray_create(tmp_tid4, mn, marray);
				log.debug("H5T_ARRAY {} Member is class {} of size={} with baseType={}", member_name, member_class, member_size, baseType);
            }

            try {
                if (H5Datatype.isUnsigned(baseType)) {
                    compInfo[2] = 1;
                }
            }
            catch (Exception ex2) {
            	log.debug("baseType isUnsigned:", ex2);
            }

            member_size = H5.H5Tget_size(member_tid);

            // construct nested compound structure with a single field
            String theName = member_name;
            tmp_tid1 = H5.H5Tcopy(arrayType);
            int sep = member_name.lastIndexOf(CompoundDS.separator);

            while (sep > 0) {
                theName = member_name.substring(sep + 1);
                nested_tid = H5.H5Tcreate(HDF5Constants.H5T_COMPOUND, member_size);
                H5.H5Tinsert(nested_tid, theName, 0, tmp_tid1);
                try {
                    H5.H5Tclose(tmp_tid1);
                }
                catch (Exception ex) {
                	log.debug("close nested temp {}:", sep, ex);
                }
                tmp_tid1 = nested_tid;
                member_name = member_name.substring(0, sep);
                sep = member_name.lastIndexOf(CompoundDS.separator);
            }

            nested_tid = H5.H5Tcreate(HDF5Constants.H5T_COMPOUND, member_size);
            
            H5.H5Tinsert(nested_tid, member_name, 0, tmp_tid1);
        }
        finally {
            try {
                H5.H5Tclose(tmp_tid1);
            }
            catch (HDF5Exception ex3) {
            	log.debug("finally close:", ex3);
            }
            try {
                H5.H5Tclose(tmp_tid4);
            }
            catch (HDF5Exception ex3) {
            	log.debug("finally close:", ex3);
            }
        }

        return nested_tid;
    }

}
