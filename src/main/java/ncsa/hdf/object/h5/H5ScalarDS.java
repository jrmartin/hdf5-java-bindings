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
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;
import ncsa.hdf.hdf5lib.structs.H5O_info_t;
import ncsa.hdf.object.Attribute;
import ncsa.hdf.object.Dataset;
import ncsa.hdf.object.Datatype;
import ncsa.hdf.object.FileFormat;
import ncsa.hdf.object.Group;
import ncsa.hdf.object.HObject;
import ncsa.hdf.object.ScalarDS;

/**
 * H5ScalarDS describes a multi-dimension array of HDF5 scalar or atomic data
 * types, such as byte, int, short, long, float, double and string, and
 * operations performed on the scalar dataset.
 * <p>
 * The library predefines a modest number of datatypes. For details, read <a
 * href="http://hdfgroup.org/HDF5/doc/Datatypes.html">The Datatype Interface
 * (H5T).</a>
 * <p>
 * 
 * @version 1.1 9/4/2007
 * @author Peter X. Cao
 */
public class H5ScalarDS extends ScalarDS {
    /**
     * 
     */
    private static final long serialVersionUID  = 2887517608230611642L;

    private final static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(H5ScalarDS.class);

    /**
     * The list of attributes of this data object. Members of the list are
     * instance of Attribute.
     */
    private List<Attribute>              attributeList;

    /**
     * The byte array containing references of palettes. Each reference requires
     * eight bytes storage. Therefore, the array length is 8*numberOfPalettes.
     */
    private byte[]            paletteRefs;

    /** flag to indicate if the dataset is a variable length */
    private boolean           isVLEN            = false;

    /** flag to indicate if the dataset is enum */
    private boolean           isEnum            = false;

    /** flag to indicate if the dataset is an external dataset */
    private boolean           isExternal        = false;

    private boolean           isArrayOfCompound = false;

    private boolean           isArrayOfVLEN     = false;
    /**
     * flag to indicate if the datatype in file is the same as dataype in memory
     */
    private boolean           isNativeDatatype  = true;

    /** flag to indicate is the datatype is reg. ref. */
    private boolean           isRegRef          = false;

    private int               nAttributes       = -1;

    private H5O_info_t        obj_info;

    /**
     * Constructs an instance of a H5ScalarDS object with specific name and
     * path.
     * <p>
     * For example, in H5ScalarDS(h5file, "dset", "/arrays/"), "dset" is the
     * name of the dataset, "/arrays" is the group path of the dataset.
     * 
     * @param theFile
     *            the file that contains the data object.
     * @param theName
     *            the name of the data object, e.g. "dset".
     * @param thePath
     *            the full path of the data object, e.g. "/arrays/".
     */
    public H5ScalarDS(FileFormat theFile, String theName, String thePath) {
        this(theFile, theName, thePath, null);
    }

    /**
     * @deprecated Not for public use in the future.<br>
     *             Using {@link #H5ScalarDS(FileFormat, String, String)}
     */
    @Deprecated
    public H5ScalarDS(FileFormat theFile, String theName, String thePath, long[] oid) {
        super(theFile, theName, thePath, oid);
        unsignedConverted = false;
        paletteRefs = null;
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
            int did = open();
            if(did >= 0) {
	            // test if it is an image
	            int tid = -1;
	            obj_info.num_attrs = 0;
	
	            try {
	                obj_info = H5.H5Oget_info(did);
	                nAttributes = (int) obj_info.num_attrs;
	
	                tid = H5.H5Dget_type(did);
	
	                int tclass = H5.H5Tget_class(tid);
	                isText = (tclass == HDF5Constants.H5T_STRING);
	                isVLEN = ((tclass == HDF5Constants.H5T_VLEN) || H5.H5Tis_variable_str(tid));
	                isEnum = (tclass == HDF5Constants.H5T_ENUM);
	            }
	            catch (Exception ex) {
	            	log.debug("test if it is an image:", ex);
	            }
	            finally {
	                try { 
	                	H5.H5Tclose(tid); 
	                }
	                catch (HDF5Exception ex) {
	                	log.debug("finally close:", ex);
	                }
	            }
	
	            // check image 
	            Object avalue = getAttrValue(did, "CLASS");
	            if (avalue!=null) {
	            	try { 
	                	isImageDisplay = isImage =  "IMAGE".equalsIgnoreCase(new String((byte[])avalue).trim());
	            	} 
	            	catch (Throwable err) {
	            		log.debug("check image:", err);
	            	}
	            }
	            
	            // retrieve the IMAGE_MINMAXRANGE
	            avalue = getAttrValue(did, "IMAGE_MINMAXRANGE");
	            if (avalue!=null) {
	                double x0 = 0, x1 = 0;
	                try {
	                    x0 = Double.valueOf(java.lang.reflect.Array.get(avalue, 0).toString()).doubleValue();
	                    x1 = Double.valueOf(java.lang.reflect.Array.get(avalue, 1).toString()).doubleValue();
	                }
	                catch (Exception ex2) {
	                    x0 = x1 = 0;
	                }
	                if (x1 > x0) {
	                    imageDataRange = new double[2];
	                    imageDataRange[0] = x0;
	                    imageDataRange[1] = x1;
	                }
	            }
	
	            try { 
	                checkCFconvention(did);
	            } 
	            catch (Exception ex) {
	            	log.debug("checkCFconvention({}):", did, ex);
	            }
	
	            close(did);
	            }
            else {
            	log.debug("could not open dataset");
            }
        }

        return (obj_info.num_attrs > 0);
    }

    // check _FillValue, valid_min, valid_max, and valid_range 
    private void checkCFconvention(int oid) throws Exception {
    	Object avalue = getAttrValue(oid, "_FillValue");
    	
    	if (avalue!=null) {
    		int n = Array.getLength(avalue);
    		for (int i=0; i<n; i++)
    			addFilteredImageValue((Number)Array.get(avalue,  i));
    	}
    	
    	if (imageDataRange==null || imageDataRange[1]<=imageDataRange[0]) {
            double x0 = 0, x1 = 0;
            avalue = getAttrValue(oid, "valid_range");
            if (avalue !=null) {
                try {
                    x0 = Double.valueOf(java.lang.reflect.Array.get(avalue, 0).toString()).doubleValue();
                    x1 = Double.valueOf(java.lang.reflect.Array.get(avalue, 1).toString()).doubleValue();
                	imageDataRange = new double[2];
                    imageDataRange[0] = x0;
                	imageDataRange[1] = x1;
                	return;
                } 
                catch (Exception ex) {
                	log.debug("valid_range:", ex);
                }            	
            }

            avalue = getAttrValue(oid, "valid_min");
            if (avalue !=null) {
                try {
                    x0 = Double.valueOf(java.lang.reflect.Array.get(avalue, 0).toString()).doubleValue();
                } 
                catch (Exception ex) {
                	log.debug("valid_min:", ex);
                }  
                avalue = getAttrValue(oid, "valid_max");
                if (avalue !=null) {
                    try {
                        x1 = Double.valueOf(java.lang.reflect.Array.get(avalue, 0).toString()).doubleValue();
                    	imageDataRange = new double[2];
                        imageDataRange[0] = x0;
                    	imageDataRange[1] = x1;                        
                    } 
                    catch (Exception ex) {
                    	log.debug("valid_max:", ex);
                    }  
                }
            }
    	} // if (imageDataRange==null || imageDataRange[1]<=imageDataRange[0])
    }
    
    private Object getAttrValue(int oid, String aname) {
    	int aid = -1, atid=-1, asid=-1;
    	Object avalue = null;
		log.trace("getAttrValue: start name={}", aname);
    	
        try {
            // try to find attribute name
            aid = H5.H5Aopen_by_name(oid, ".", aname, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
        }
        catch (HDF5LibraryException ex5) {
        	log.debug("Failed to find attribute {} : Expected", aname);
        }
        catch (Exception ex) {
        	log.debug("try to find attribute {}:", aname, ex);
        }
        if (aid > 0) {
        	try {
        		atid = H5.H5Aget_type(aid);
        		int tmptid = atid;
        		atid = H5.H5Tget_native_type(tmptid);
        		try {
        			H5.H5Tclose(tmptid);
        		}
        		catch (Exception ex) {
        			log.debug("close H5Aget_type after getting H5Tget_native_type:", ex);
        		}

        		asid = H5.H5Aget_space(aid);
        		long adims[] = null;

        		int arank = H5.H5Sget_simple_extent_ndims(asid);
        		if (arank > 0) {
        			adims = new long[arank];
        			H5.H5Sget_simple_extent_dims(asid, adims, null);
        		}
        		log.trace("getAttrValue: adims={}", adims);

        		// retrieve the attribute value
        		long lsize = 1;
        		for (int j = 0; j < adims.length; j++) {
        			lsize *= adims[j];
        		}
        		log.trace("getAttrValue: lsize={}", lsize);
        		avalue = H5Datatype.allocateArray(atid, (int) lsize);

        		if (avalue != null) {
    				log.trace("read attribute id {} of size={}", atid, lsize);
        			H5.H5Aread(aid, atid, avalue);

        			if (H5Datatype.isUnsigned(atid)) {
        				log.trace("id {} is unsigned", atid);
        				avalue = convertFromUnsignedC(avalue, null);
        			}
        		}
        	}
        	catch (Exception ex) {
        		log.debug("try to get value for attribute {}:", aname, ex);
        	}
        	finally {
        		try {
        			H5.H5Tclose(atid);
        		}
        		catch (HDF5Exception ex) {
        			log.debug("finally close:", ex);
        		}
        		try {
        			H5.H5Sclose(asid);
        		}
        		catch (HDF5Exception ex) {
        			log.debug("finally close:", ex);
        		}
        		try {
        			H5.H5Aclose(aid);
        		}
        		catch (HDF5Exception ex) {
        			log.debug("finally close:", ex);
        		}
        	}
        } // if (aid > 0)
    	
		log.trace("getAttrValue: finish");
        return avalue;
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
		log.trace("init() start");

        int did = -1, sid = -1, tid = -1;

        did = open();
        if(did >= 0) {
        	paletteRefs = getPaletteRefs(did);

        	int pid = -1;
        	try {
        		sid = H5.H5Dget_space(did);
        		tid = H5.H5Dget_type(did);

        		int tclass = H5.H5Tget_class(tid);
        		rank = H5.H5Sget_simple_extent_ndims(sid);

        		isText = (tclass == HDF5Constants.H5T_STRING);
        		isVLEN = ((tclass == HDF5Constants.H5T_VLEN) || H5.H5Tis_variable_str(tid));
        		isEnum = (tclass == HDF5Constants.H5T_ENUM);
        		isUnsigned = H5Datatype.isUnsigned(tid);
        		isRegRef = H5.H5Tequal(tid, HDF5Constants.H5T_STD_REF_DSETREG);
        		log.debug("init() tid={} is tclass={} has isText={} : isVLEN={} : isEnum={} : isUnsigned={} : isRegRef={}", tid, tclass, isText, isVLEN, isEnum, isUnsigned, isRegRef);

        		if (tclass == HDF5Constants.H5T_ARRAY) {
        			// use the base datatype to define the array
        			int basetid = H5.H5Tget_super(tid);
        			int baseclass = H5.H5Tget_class(basetid);
        			isArrayOfCompound = (baseclass == HDF5Constants.H5T_COMPOUND);
        			isArrayOfVLEN = (baseclass == HDF5Constants.H5T_VLEN);
        		}

        		// check if it is an external dataset
        		try {
        			pid = H5.H5Dget_create_plist(did);
        			int nfiles = H5.H5Pget_external_count(pid);
        			isExternal = (nfiles > 0);
        		}
        		catch (Exception ex) {
        			log.debug("check if it is an external dataset:", ex);
        		}

        		// check if datatype in file is native datatype
        		int tmptid = 0;
        		try {
        			tmptid = H5.H5Tget_native_type(tid);
        			isNativeDatatype = H5.H5Tequal(tid, tmptid);

        			/* see if fill value is defined */
        			int[] fillStatus = { 0 };
        			if (H5.H5Pfill_value_defined(pid, fillStatus) >= 0) {
        				if (fillStatus[0] == HDF5Constants.H5D_FILL_VALUE_USER_DEFINED) {
        					fillValue = H5Datatype.allocateArray(tmptid, 1);
        					try {
        						H5.H5Pget_fill_value(pid, tmptid, fillValue);
        						if (fillValue !=null) {
        							if(isFillValueConverted)
        								fillValue = ScalarDS.convertToUnsignedC(fillValue, null);

        							int n = Array.getLength(fillValue);
        							for (int i=0; i<n; i++)
        								addFilteredImageValue((Number)Array.get(fillValue,  i));
        						}
        					}
        					catch (Exception ex2) {
        						log.debug("fill value was defined :", ex2);
        						fillValue = null;
        					}
        				}
        			}
        		}
        		catch (HDF5Exception ex) {
					log.debug("check if datatype in file is native datatype :", ex);
        		}
        		finally {
        			try {
        				H5.H5Tclose(tmptid);
        			}
        			catch (HDF5Exception ex) {
        				log.debug("finally close:", ex);
        			}
        			try {
        				H5.H5Pclose(pid);
        			}
        			catch (Exception ex) {
        				log.debug("finally close:", ex);
        			}
        		}

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
        	}
        	catch (HDF5Exception ex) {
        		log.debug("init():", ex);
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
        	}

        	// check for the type of image and interlace mode
        	// it is a true color image at one of three cases:
        	// 1) IMAGE_SUBCLASS = IMAGE_TRUECOLOR,
        	// 2) INTERLACE_MODE = INTERLACE_PIXEL,
        	// 3) INTERLACE_MODE = INTERLACE_PLANE
        	if ((rank >= 3) && isImage) {
        		interlace = -1;
        		isTrueColor = isStringAttributeOf(did, "IMAGE_SUBCLASS", "IMAGE_TRUECOLOR");

        		if (isTrueColor) {
        			interlace = INTERLACE_PIXEL;
        			if (isStringAttributeOf(did, "INTERLACE_MODE", "INTERLACE_PLANE")) {
        				interlace = INTERLACE_PLANE;
        			}
        		}
        	}

        	close(did);
        }
    	else {
    		log.debug("init() failed to open dataset");
    	}

        startDims = new long[rank];
        selectedDims = new long[rank];
		log.trace("init() finish");
        resetSelection();
    }

    private boolean isStringAttributeOf(int objID, String name, String value) {
        boolean retValue = false;
        int aid = -1, atid = -1;

        try {
            // try to find out interlace mode
            aid = H5.H5Aopen_by_name(objID, ".", name, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
            atid = H5.H5Aget_type(aid);
            int size = H5.H5Tget_size(atid);
            byte[] attrValue = new byte[size];
            H5.H5Aread(aid, atid, attrValue);
            String strValue = new String(attrValue).trim();
            retValue = strValue.equalsIgnoreCase(value);
        }
        catch (Exception ex) {
        	log.debug("try to find out interlace mode:", ex);
        }
        finally {
            try {
                H5.H5Tclose(atid);
            }
            catch (HDF5Exception ex) {
            	log.debug("finally close:", ex);
            }
            try {
                H5.H5Aclose(aid);
            }
            catch (HDF5Exception ex) {
            	log.debug("finally close:", ex);
            }
        }

        return retValue;
    }

    /**
     * Resets selection of dataspace
     */
    private void resetSelection() {
    	log.trace("resetSelection: start");

        for (int i = 0; i < rank; i++) {
            startDims[i] = 0;
            selectedDims[i] = 1;
            if (selectedStride != null) {
                selectedStride[i] = 1;
            }
        }

        if (interlace == INTERLACE_PIXEL) {
            // 24-bit TRUE color image
            // [height][width][pixel components]
            selectedDims[2] = 3;
            selectedDims[0] = dims[0];
            selectedDims[1] = dims[1];
            selectedIndex[0] = 0; // index for height
            selectedIndex[1] = 1; // index for width
            selectedIndex[2] = 2; // index for depth
        }
        else if (interlace == INTERLACE_PLANE) {
            // 24-bit TRUE color image
            // [pixel components][height][width]
            selectedDims[0] = 3;
            selectedDims[1] = dims[1];
            selectedDims[2] = dims[2];
            selectedIndex[0] = 1; // index for height
            selectedIndex[1] = 2; // index for width
            selectedIndex[2] = 0; // index for depth
        }
        else if (rank == 1) {
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
            // // hdf-java 2.5 version: 3D dataset is arranged in the order of
            // [frame][height][width] by default
            // selectedIndex[1] = rank-1; // width, the fastest dimension
            // selectedIndex[0] = rank-2; // height
            // selectedIndex[2] = rank-3; // frames

            //
            // (5/4/09) Modified the default dimension order. See bug#1379
            // We change the default order to the following. In most situation,
            // users want to use the nature order of
            // selectedIndex[0] = 0
            // selectedIndex[1] = 1
            // selectedIndex[2] = 2
            // Most of NPOESS data is the the order above.

            if (isImage) {
                // 3D dataset is arranged in the order of [frame][height][width]
                selectedIndex[1] = rank - 1; // width, the fastest dimension
                selectedIndex[0] = rank - 2; // height
                selectedIndex[2] = rank - 3; // frames
            }
            else {
                selectedIndex[0] = 0; // width, the fastest dimension
                selectedIndex[1] = 1; // height
                selectedIndex[2] = 2; // frames
            }

            selectedDims[selectedIndex[0]] = dims[selectedIndex[0]];
            selectedDims[selectedIndex[1]] = dims[selectedIndex[1]];
        }

        // by default, only one-D is selected for text data
        if ((rank > 1) && isText) {
            selectedIndex[0] = rank - 1;
            selectedIndex[1] = 0;
            selectedDims[0] = 1;
            selectedDims[selectedIndex[0]] = dims[selectedIndex[0]];
        }

        isDataLoaded = false;
        isDefaultImageOrder = true;
    	log.trace("resetSelection: finish");
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
            ((Vector) attributeList).setSize(0);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.Dataset#copy(ncsa.hdf.object.Group,
     * java.lang.String, long[], java.lang.Object)
     */
    @Override
    public Dataset copy(Group pgroup, String dstName, long[] dims, Object buff) throws Exception {
        // must give a location to copy
        if (pgroup == null) {
            return null;
        }

        Dataset dataset = null;
        int srcdid = -1, dstdid = -1, tid = -1, sid = -1, plist = -1;
        String dname = null, path = null;

        if (pgroup.isRoot()) {
            path = HObject.separator;
        }
        else {
            path = pgroup.getPath() + pgroup.getName() + HObject.separator;
        }
        dname = path + dstName;

        srcdid = open();
        if(srcdid >= 0) {
        	try {
        		tid = H5.H5Dget_type(srcdid);
        		sid = H5.H5Screate_simple(dims.length, dims, null);
        		plist = H5.H5Dget_create_plist(srcdid);

        		long[] chunks = new long[dims.length];
        		boolean setChunkFlag = false;
        		try {
        			H5.H5Pget_chunk(plist, dims.length, chunks);
        			for (int i = 0; i < dims.length; i++) {
        				if (dims[i] < chunks[i]) {
        					setChunkFlag = true;
        					if (dims[i] == 1)
        						chunks[i] = 1;
        					else
        						chunks[i] = dims[i] / 2;
        				}
        			}
        		}
        		catch (Exception ex) {
        			log.debug("copy chunk:", ex);
        		}

        		if (setChunkFlag) H5.H5Pset_chunk(plist, dims.length, chunks);

        		try {
        			dstdid = H5.H5Dcreate(pgroup.getFID(), dname, tid, sid, HDF5Constants.H5P_DEFAULT, plist,
        					HDF5Constants.H5P_DEFAULT);
        		}
        		catch (Exception e) {
        			log.debug("copy create:", e);
        		}
        		finally {
        			try {
        				H5.H5Dclose(dstdid);
        			}
        			catch (Exception ex2) {
        				log.debug("finally close:", ex2);
        			}
        		}

        		dataset = new H5ScalarDS(pgroup.getFileFormat(), dstName, path);
        		if (buff != null) {
        			dataset.init();
        			dataset.write(buff);
        		}

        		dstdid = dataset.open();
        		if(dstdid >= 0) {
        			try {
        				H5File.copyAttributes(srcdid, dstdid);
        			}
        			finally {
        				try {
        					H5.H5Dclose(dstdid);
        				}
        				catch (Exception ex) {
        					log.debug("finally close:", ex);
        				}
        			}
        		}
        	}
        	finally {
        		try {
        			H5.H5Pclose(plist);
        		}
        		catch (Exception ex) {
        			log.debug("finally close:", ex);
        		}
        		try {
        			H5.H5Sclose(sid);
        		}
        		catch (Exception ex) {
        			log.debug("finally close:", ex);
        		}
        		try {
        			H5.H5Tclose(tid);
        		}
        		catch (Exception ex) {
        			log.debug("finally close:", ex);
        		}
        		try {
        			H5.H5Dclose(srcdid);
        		}
        		catch (Exception ex) {
        			log.debug("finally close:", ex);
        		}
        	}
        }

        pgroup.addToMemberList(dataset);

        ((ScalarDS) dataset).setIsImage(isImage);

        return dataset;
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
        Object theData = null;
        int did = -1, tid = -1;
        int spaceIDs[] = { -1, -1 }; // spaceIDs[0]=mspace, spaceIDs[1]=fspace

		log.trace("H5ScalarDS read: start");
        if (rank <= 0) {
            init();
        }

        if (isArrayOfCompound) throw new HDF5Exception("Cannot show data with datatype of ARRAY of COMPOUND.");
        if (isArrayOfVLEN) throw new HDF5Exception("Cannot show data with datatype of ARRAY of VL.");

        if (isExternal) {
            String pdir = this.getFileFormat().getAbsoluteFile().getParent();

            if (pdir == null) {
                pdir = ".";
            }
            H5.H5Dchdir_ext(pdir);
        }

        long[] lsize = { 1 };
		log.trace("H5ScalarDS read: open dataset");
        did = open();
        if(did >= 0) {
        	try {
        		lsize[0] = selectHyperslab(did, spaceIDs);
        		log.trace("H5ScalarDS read: opened dataset size {} for {}", lsize[0], nPoints);

        		if (lsize[0] == 0) {
        			throw new HDF5Exception("No data to read.\nEither the dataset or the selected subset is empty.");
        		}

        		if(log.isDebugEnabled()) {
	        		// check is storage space is allocated
	        		try {
	        			long ssize = H5.H5Dget_storage_size(did);
	       				log.debug("Storage space allocated = {}.", ssize);
	        		}
	        		catch (Exception ex) {
	        			log.debug("check if storage space is allocated:", ex);
	        		}
        		}

        		tid = H5.H5Dget_type(did);
        		if (!isNativeDatatype) {
        			int tmptid = -1;
        			try {
        				tmptid = tid;
        				tid = H5.H5Tget_native_type(tmptid);
        			}
        			finally {
        				try {
        					H5.H5Tclose(tmptid);
        				}
        				catch (Exception ex2) {
        					log.debug("finally close:", ex2);
        				}
        			}
        		}

        		boolean isREF = (H5.H5Tequal(tid, HDF5Constants.H5T_STD_REF_OBJ));

        		if ((originalBuf == null) || isText || isREF || ((originalBuf != null) && (lsize[0] != nPoints))) {
        			try {
        				theData = H5Datatype.allocateArray(tid, (int) lsize[0]);
        			}
        			catch (OutOfMemoryError err) {
        				throw new HDF5Exception("Out Of Memory.");
        			}
        		}
        		else {
        			theData = originalBuf; // reuse the buffer if the size is the
        			// same
        		}

        		if (theData != null) {
        			if (isVLEN) {
        				log.trace("H5ScalarDS read: H5DreadVL");
        				H5.H5DreadVL(did, tid, spaceIDs[0], spaceIDs[1], HDF5Constants.H5P_DEFAULT, (Object[]) theData);
        			}
        			else {
        				log.trace("H5ScalarDS read: H5Dread");
        				H5.H5Dread(did, tid, spaceIDs[0], spaceIDs[1], HDF5Constants.H5P_DEFAULT, theData);

        				if (isText && convertByteToString) {
        					theData = byteToString((byte[]) theData, H5.H5Tget_size(tid));
        				}
        				else if (isREF) {
        					theData = HDFNativeData.byteToLong((byte[]) theData);
        				}
        				else if (isEnum && isEnumConverted()) {
        					theData = H5Datatype.convertEnumValueToName(tid, theData, null);
        				}
        			}
        		} // if (theData != null)
        	}
    		catch (HDF5Exception h5ex) {
    			log.debug("H5ScalarDS read: read failure", h5ex);
    			throw new HDF5Exception(h5ex.toString());
    		}
        	finally {
        		try {
        			if(HDF5Constants.H5S_ALL != spaceIDs[0])
        				H5.H5Sclose(spaceIDs[0]);
        		}
        		catch (Exception ex) {
        			log.debug("finally close:", ex);
        		}
        		try {
        			if(HDF5Constants.H5S_ALL != spaceIDs[1])
        				H5.H5Sclose(spaceIDs[1]);
        		}
        		catch (Exception ex) {
        			log.debug("finally close:", ex);
        		}
        		try {
        			H5.H5Tclose(tid);
        		}
        		catch (Exception ex2) {
        			log.debug("finally close:", ex2);
        		}
        		close(did);
        	}
        }

		log.trace("H5ScalarDS read: finish");
        return theData;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.Dataset#write(java.lang.Object)
     */
    @Override
    public void write(Object buf) throws HDF5Exception {
        int did = -1, tid = -1;
        int spaceIDs[] = { -1, -1 }; // spaceIDs[0]=mspace, spaceIDs[1]=fspace
        Object tmpData = null;

        if (buf == null) {
            return;
        }

        if (isVLEN && !isText) {
            throw (new HDF5Exception("Writing non-string variable-length data is not supported"));
        }
        else if (isRegRef) {
            throw (new HDF5Exception("Writing region references data is not supported"));
        }

        long[] lsize = { 1 };
        did = open();
        if(did >= 0) {
        	try {
        		lsize[0] = selectHyperslab(did, spaceIDs);
        		tid = H5.H5Dget_type(did);

        		if (!isNativeDatatype) {
        			int tmptid = -1;
        			try {
        				tmptid = tid;
        				tid = H5.H5Tget_native_type(tmptid);
        			}
        			finally {
        				try {
        					H5.H5Tclose(tmptid);
        				}
        				catch (Exception ex2) {
        					log.debug("finally close:", ex2);
        				}
        			}
        		}

        		isText = (H5.H5Tget_class(tid) == HDF5Constants.H5T_STRING);

        		// check if need to convert integer data
        		int tsize = H5.H5Tget_size(tid);
        		String cname = buf.getClass().getName();
        		char dname = cname.charAt(cname.lastIndexOf("[") + 1);
        		boolean doConversion = (((tsize == 1) && (dname == 'S')) || ((tsize == 2) && (dname == 'I'))
        				|| ((tsize == 4) && (dname == 'J')) || (isUnsigned && unsignedConverted));

        		tmpData = buf;
        		if (doConversion) {
        			tmpData = convertToUnsignedC(buf, null);
        		}
        		// do not convert v-len strings, regardless of conversion request
        		// type
        		else if (isText && convertByteToString && !H5.H5Tis_variable_str(tid)) {
        			tmpData = stringToByte((String[]) buf, H5.H5Tget_size(tid));
        		}
        		else if (isEnum && (Array.get(buf, 0) instanceof String)) {
        			tmpData = H5Datatype.convertEnumNameToValue(tid, (String[]) buf, null);
        		}

        		H5.H5Dwrite(did, tid, spaceIDs[0], spaceIDs[1], HDF5Constants.H5P_DEFAULT, tmpData);

        	}
        	finally {
        		tmpData = null;
        		try {
        			if(HDF5Constants.H5S_ALL != spaceIDs[0])
        				H5.H5Sclose(spaceIDs[0]);
        		}
        		catch (Exception ex) {
        			log.debug("finally close:", ex);
        		}
        		try {
        			if(HDF5Constants.H5S_ALL != spaceIDs[1])
        				H5.H5Sclose(spaceIDs[1]);
        		}
        		catch (Exception ex) {
        			log.debug("finally close:", ex);
        		}
        		try {
        			H5.H5Tclose(tid);
        		}
        		catch (Exception ex) {
        			log.debug("finally close:", ex);
        		}
        		close(did);
        	}
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

        if ((rank > 1) && (selectedIndex[0] > selectedIndex[1]))
            isDefaultImageOrder = false;
        else
            isDefaultImageOrder = true;

        return lsize;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.DataFormat#getMetadata()
     */
    public List getMetadata() throws HDF5Exception {
        return this.getMetadata(fileFormat.getIndexType(null), fileFormat.getIndexOrder(null));
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.DataFormat#getMetadata(int...)
     */
    public List getMetadata(int... attrPropList) throws HDF5Exception {
        if (rank <= 0) {
            init();
        }
		log.trace("getMetadata: inited");

        try {
            this.linkTargetObjName = H5File.getLinkTargetName(this);
        }
        catch (Exception ex) {
        	log.debug("getLinkTargetName failed: ", ex);
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
		log.trace("getMetadata: open dataset");
        did = open();
        if(did >= 0) {
    		log.trace("getMetadata: dataset opened");
        	try {
        		attributeList = H5File.getAttribute(did, indxType, order);
        		log.trace("getMetadata: attributeList loaded");

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
        		long[] cd_nelmts = { 0 };
        		int[] cd_values = null;
        		String[] cd_name = { "", "" };
        		int nfilt = H5.H5Pget_nfilters(pid);
        		log.debug("getMetadata: {} filters in pipeline", nfilt);
        		int filter = -1;
        		int[] filter_config = { 1 };
        		compression = "";

        		for (int i = 0; i < nfilt; i++) {
            		log.debug("getMetadata: filter[{}]", i);
        			if (i > 0) {
        				compression += ", ";
        			}

        			try {
        				cd_nelmts[0] = 0;
        				cd_values = null;
        				filter = H5.H5Pget_filter(pid, i, flags, cd_nelmts, cd_values, 120, cd_name, filter_config);
                		log.debug("getMetadata: filter[{}] is {} has {} elements ", i, cd_name[0], cd_nelmts[0]);
                		if(cd_nelmts[0] < 1)
                			cd_nelmts[0] = 1;
        				cd_values = new int[(int) cd_nelmts[0]];
        				filter = H5.H5Pget_filter(pid, i, flags, cd_nelmts, cd_values, 120, cd_name, filter_config);
                		log.debug("getMetadata: filter[{}] is {} has {} elements ", i, cd_name[0], cd_nelmts[0]);
    					for (int j=0; j<cd_nelmts[0]; j++) {
    						log.debug("getMetadata: filter[{}] element {} = {}", i, j, cd_values[j]);
    					}
        			}
        			catch (Throwable err) {
        				compression += "ERROR";
        				continue;
        			}

        			if (filter == HDF5Constants.H5Z_FILTER_NONE) {
        				compression += "NONE";
        			}
        			else if (filter == HDF5Constants.H5Z_FILTER_DEFLATE) {
        				compression += "GZIP: level = " + cd_values[0];
        			}
        			else if (filter == HDF5Constants.H5Z_FILTER_FLETCHER32) {
        				compression += "Error detection filter";
        			}
        			else if (filter == HDF5Constants.H5Z_FILTER_SHUFFLE) {
        				compression += "SHUFFLE: Nbytes = " + cd_values[0];
        			}
        			else if (filter == HDF5Constants.H5Z_FILTER_NBIT) {
        				compression += "NBIT";
        			}
        			else if (filter == HDF5Constants.H5Z_FILTER_SCALEOFFSET) {
        				compression += "SCALEOFFSET: MIN BITS = " + cd_values[0];
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
        			else {
    					compression += "USERDEFINED " + filter + ": ";
    					for (int j=0; j<cd_nelmts[0]; j++) {
    						if(j > 0)
            					compression += ", ";
    						compression += cd_values[j];
    					}
                		log.debug("getMetadata: filter[{}] is user defined compression", i);
        			}
        		} // for (int i=0; i<nfilt; i++)

        		if (compression.length() == 0) {
        			compression = "NONE";
        		}
        		log.trace("getMetadata: filter compression={}", compression);

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
        		log.trace("getMetadata: compression={}", compression);
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

		log.trace("getMetadata: finish");
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
        String name = attr.getName();

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
	            List attrList = getMetadata();
	            attrList.remove(attr);
	            nAttributes = attributeList.size();
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
        	log.debug("dataset {} open failed", getPath() + getName());
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
	        	log.debug("close flush:", ex);
	        }
	        try {
	            H5.H5Dclose(did);
	        }
	        catch (HDF5Exception ex) {
	        	log.debug("close:", ex);
	        }
	    }
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.ScalarDS#getPalette()
     */
    @Override
    public byte[][] getPalette() {
        if (palette == null) {
            palette = readPalette(0);
        }

        return palette;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.ScalarDS#getPaletteName(int)
     */
    public String getPaletteName(int idx) {

        byte[] refs = getPaletteRefs();
        int did = -1, pal_id = -1;
        String[] paletteName = { "" };
        long size = 100L;

        if (refs == null) {
            return null;
        }

        byte[] ref_buf = new byte[8];

        try {
            System.arraycopy(refs, idx * 8, ref_buf, 0, 8);
        }
        catch (Throwable err) {
            return null;
        }

        did = open();
        if(did >= 0) {
	        try {
	            pal_id = H5.H5Rdereference(getFID(), HDF5Constants.H5R_OBJECT, ref_buf);
	            H5.H5Iget_name(pal_id, paletteName, size);
	        }
	        catch (Exception ex) {
	            ex.printStackTrace();
	        }
	        finally {
	            close(pal_id);
	            close(did);
	        }
        }

        return paletteName[0];
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.ScalarDS#readPalette(int)
     */
    @Override
    public byte[][] readPalette(int idx) {
        byte[][] thePalette = null;
        byte[] refs = getPaletteRefs();
        int did = -1, pal_id = -1, tid = -1;

        if (refs == null) {
            return null;
        }

        byte[] p = null;
        byte[] ref_buf = new byte[8];

        try {
            System.arraycopy(refs, idx * 8, ref_buf, 0, 8);
        }
        catch (Throwable err) {
            return null;
        }

        did = open();
        if(did >= 0) {
        	try {
        		pal_id = H5.H5Rdereference(getFID(), HDF5Constants.H5R_OBJECT, ref_buf);
        		tid = H5.H5Dget_type(pal_id);

        		// support only 3*256 byte palette data
        		if (H5.H5Dget_storage_size(pal_id) <= 768) {
        			p = new byte[3 * 256];
        			H5.H5Dread(pal_id, tid, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, p);
        		}
        	}
        	catch (HDF5Exception ex) {
        		p = null;
        	}
        	finally {
        		try {
        			H5.H5Tclose(tid);
        		}
        		catch (HDF5Exception ex2) {
        		}
        		close(pal_id);
        		close(did);
        	}
        }

        if (p != null) {
            thePalette = new byte[3][256];
            for (int i = 0; i < 256; i++) {
                thePalette[0][i] = p[i * 3];
                thePalette[1][i] = p[i * 3 + 1];
                thePalette[2][i] = p[i * 3 + 2];
            }
        }

        return thePalette;
    }

    private static byte[] parseFillValue(Datatype type, Object fillValue) throws Exception {
        byte[] data = null;

        if (type == null || fillValue == null) return null;

        int datatypeClass = type.getDatatypeClass();
        int datatypeSize = type.getDatatypeSize();

        double val_dbl = 0;
        String val_str = null;

        if (fillValue instanceof String) {
            val_str = (String) fillValue;
        }
        else if (fillValue.getClass().isArray()) {
            val_str = Array.get(fillValue, 0).toString();
        }

        if (datatypeClass != Datatype.CLASS_STRING) {
            try {
                val_dbl = Double.parseDouble(val_str);
            }
            catch (NumberFormatException ex) {
                return null;
            }
        }

        try {
            switch (datatypeClass) {
                case Datatype.CLASS_INTEGER:
                case Datatype.CLASS_ENUM:
                case Datatype.CLASS_CHAR:
                    if (datatypeSize == 1) {
                        data = new byte[] { (byte) val_dbl };
                    }
                    else if (datatypeSize == 2) {
                        data = HDFNativeData.shortToByte((short) val_dbl);
                    }
                    else if (datatypeSize == 8) {
                        data = HDFNativeData.longToByte((long) val_dbl);
                    }
                    else {
                        data = HDFNativeData.intToByte((int) val_dbl);
                    }
                    break;
                case Datatype.CLASS_FLOAT:
                    if (datatypeSize == 8) {
                        data = HDFNativeData.doubleToByte(val_dbl);
                    }
                    else {
                        data = HDFNativeData.floatToByte((float) val_dbl);
                        ;
                    }
                    break;
                case Datatype.CLASS_STRING:
                    data = val_str.getBytes();
                    break;
                case Datatype.CLASS_REFERENCE:
                    data = HDFNativeData.longToByte((long) val_dbl);
                    break;
            } // switch (tclass)
        }
        catch (Exception ex) {
            data = null;
        }

        return data;
    }

    /**
     * Creates a new dataset in a file.
     * <p>
     * The following example shows how to create a string dataset using this
     * function.
     * 
     * <pre>
     * H5File file = new H5File(&quot;test.h5&quot;, H5File.CREATE);
     * int max_str_len = 120;
     * Datatype strType = new H5Datatype(Datatype.CLASS_STRING, max_str_len, -1, -1);
     * int size = 10000;
     * long dims[] = { size };
     * long chunks[] = { 1000 };
     * int gzip = 9;
     * String strs[] = new String[size];
     * 
     * for (int i = 0; i &lt; size; i++)
     *     strs[i] = String.valueOf(i);
     * 
     * file.open();
     * file.createScalarDS(&quot;/1D scalar strings&quot;, null, strType, dims, null, chunks, gzip, strs);
     * 
     * try {
     *     file.close();
     * }
     * catch (Exception ex) {
     * }
     * </pre>
     * 
     * @param name
     *            the name of the dataset to create.
     * @param pgroup
     *            the parent group of the new dataset.
     * @param type
     *            the datatype of the dataset.
     * @param dims
     *            the dimension size of the dataset.
     * @param maxdims
     *            the max dimension size of the dataset. maxdims is set to dims
     *            if maxdims = null.
     * @param chunks
     *            the chunk size of the dataset. No chunking if chunk = null.
     * @param gzip
     *            the level of the gzip compression. No compression if gzip<=0.
     * @param data
     *            the array of data values.
     * 
     * @return the new dataset if successful. Otherwise returns null.
     */
    public static H5ScalarDS create(String name, Group pgroup, Datatype type, long[] dims, long[] maxdims,
            long[] chunks, int gzip, Object fillValue, Object data) throws Exception {
        H5ScalarDS dataset = null;
        String fullPath = null;
        int did = -1, tid = -1, sid = -1, plist = -1;

        if ((pgroup == null) || (name == null) || (dims == null) || ((gzip > 0) && (chunks == null))) {
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
        // datasets. Chunking makes it possible to extend datasets efficiently,
        // without having to reorganize storage excessively. Using default size
        // of 64x...which has good performance
        if ((chunks == null) && isExtentable) {
            chunks = new long[dims.length];
            for (int i = 0; i < dims.length; i++)
                chunks[i] = Math.min(dims[i], 64);
        }

        // prepare the dataspace and datatype
        int rank = dims.length;

        if((tid = type.toNative()) >= 0) {
        	try {
        		sid = H5.H5Screate_simple(rank, dims, maxdims);

        		// figure out creation properties
        		plist = HDF5Constants.H5P_DEFAULT;

        		byte[] val_fill = null;
        		try {
        			val_fill = parseFillValue(type, fillValue);
        		}
        		catch (Exception ex) {
        			log.debug("fill value:", ex);
        		}

        		if (chunks != null || val_fill != null) {
        			plist = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE);

        			if (chunks != null) {
        				H5.H5Pset_layout(plist, HDF5Constants.H5D_CHUNKED);
        				H5.H5Pset_chunk(plist, rank, chunks);
        			}

        			if (val_fill != null) {
        				H5.H5Pset_fill_value(plist, tid, val_fill);
        			}
        		}

        		if (gzip > 0) {
        			H5.H5Pset_deflate(plist, gzip);
        		}
        		int fid = file.getFID();

        		did = H5.H5Dcreate(fid, fullPath, tid, sid, HDF5Constants.H5P_DEFAULT, plist, HDF5Constants.H5P_DEFAULT);
        		dataset = new H5ScalarDS(file, name, path);
        	}
        	finally {
        		try {
        			H5.H5Pclose(plist);
        		}
        		catch (HDF5Exception ex) {
        			log.debug("finally close:", ex);
        		}
        		try {
        			H5.H5Sclose(sid);
        		}
        		catch (HDF5Exception ex) {
        			log.debug("finally close:", ex);
        		}
        		try {
        			H5.H5Tclose(tid);
        		}
        		catch (HDF5Exception ex) {
        			log.debug("finally close:", ex);
        		}
        		try {
        			H5.H5Dclose(did);
        		}
        		catch (HDF5Exception ex) {
        			log.debug("finally close:", ex);
        		}
        	}
        }

        if (dataset != null) {
            pgroup.addToMemberList(dataset);
            if (data != null) {
                dataset.write(data);
            }
        }

        return dataset;
    }

    public static H5ScalarDS create(String name, Group pgroup, Datatype type, long[] dims, long[] maxdims,
            long[] chunks, int gzip, Object data) throws Exception {
        return create(name, pgroup, type, dims, maxdims, chunks, gzip, null, data);
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.ScalarDS#getPaletteRefs()
     */
    @Override
    public byte[] getPaletteRefs() {
        if (rank <= 0) {
            init(); // init will be called to get refs
        }

        return paletteRefs;
    }

    /**
     * reads references of palettes into a byte array Each reference requires
     * eight bytes storage. Therefore, the array length is 8*numberOfPalettes.
     */
    private byte[] getPaletteRefs(int did) {
        int aid = -1, sid = -1, size = 0, rank = 0, atype = -1;
        byte[] ref_buf = null;

        try {
            aid = H5.H5Aopen_by_name(did, ".", "PALETTE", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
            sid = H5.H5Aget_space(aid);
            rank = H5.H5Sget_simple_extent_ndims(sid);
            size = 1;
            if (rank > 0) {
                long[] dims = new long[rank];
                H5.H5Sget_simple_extent_dims(sid, dims, null);
                for (int i = 0; i < rank; i++) {
                    size *= (int) dims[i];
                }
            }

            ref_buf = new byte[size * 8];
            atype = H5.H5Aget_type(aid);

            H5.H5Aread(aid, atype, ref_buf);
        }
        catch (HDF5Exception ex) {
        	log.debug("Palette attribute search failed: Expected");
            ref_buf = null;
        }
        finally {
            try {
                H5.H5Tclose(atype);
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
            try {
                H5.H5Aclose(aid);
            }
            catch (HDF5Exception ex2) {
            	log.debug("finally close:", ex2);
            }
        }

        return ref_buf;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ncsa.hdf.object.Dataset#getDatatype()
     */
    @Override
    public Datatype getDatatype() {
        if (datatype == null) {
            int did = -1, tid = -1;

            did = open();
            if(did >= 0) {
            	try {
            		tid = H5.H5Dget_type(did);

            		if (!isNativeDatatype) {
            			int tmptid = -1;
            			try {
            				tmptid = tid;
            				tid = H5.H5Tget_native_type(tmptid);
            			}
            			finally {
            				try {
            					H5.H5Tclose(tmptid);
            				}
            				catch (Exception ex2) {
            					log.debug("finally close:", ex2);
            				}
            			}
            		}
            		datatype = new H5Datatype(tid);
            	}
            	catch (Exception ex) {
            	}
            	finally {
            		try {
            			H5.H5Tclose(tid);
            		}
            		catch (HDF5Exception ex) {
            			log.debug("finally close:", ex);
            		}
            		try {
            			H5.H5Dclose(did);
            		}
            		catch (HDF5Exception ex) {
            			log.debug("finally close:", ex);
            		}
            	}
            }
        }

        return datatype;
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
     * H5Dset_extent verifies that the dataset is at least of size size,
     * extending it if necessary. The dimensionality of size is the same as that
     * of the dataspace of the dataset being changed.
     * 
     * This function can be applied to the following datasets: 1) Any dataset
     * with unlimited dimensions 2) A dataset with fixed dimensions if the
     * current dimension sizes are less than the maximum sizes set with maxdims
     * (see H5Screate_simple)
     */
    public void extend(long[] newDims) throws HDF5Exception {
        int did = -1, sid = -1;

        did = open();
        if(did >= 0) {
        	try {
        		H5.H5Dset_extent(did, newDims);
        		H5.H5Fflush(did, HDF5Constants.H5F_SCOPE_GLOBAL);
        		sid = H5.H5Dget_space(did);
        		long[] checkDims = new long[rank];
        		H5.H5Sget_simple_extent_dims(sid, checkDims, null);
        		for (int i = 0; i < rank; i++) {
        			if (checkDims[i] != newDims[i]) {
        				throw new HDF5Exception("error extending dataset " + getName());
        			}
        		}
        		dims = checkDims;
        	}
        	catch (Exception e) {
        		throw new HDF5Exception(e.getMessage());
        	}
        	finally {
        		if (sid > 0) H5.H5Sclose(sid);

        		close(did);
        	}
        }
    }

}
