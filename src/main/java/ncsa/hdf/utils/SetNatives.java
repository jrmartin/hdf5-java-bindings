package ncsa.hdf.utils;

import java.io.File;
import java.io.FileOutputStream;
/*******************************************************************************
 * The MIT License (MIT)
 *
 * Copyright (c) 2011 - 2015 OpenWorm.
 * http://openworm.org
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the MIT License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/MIT
 *
 * Contributors:
 *     	OpenWorm - http://openworm.org/people.html
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
 * USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Set Natives for HDF
 * 
 * @author Jesus Martinez (jesus@metacell.us)
 *
 */
public class SetNatives {

	private static Log _logger = LogFactory.getLog(SetNatives.class);

	private static SetNatives natives = null;
	
	private static String WINDOWS_32 = "/natives/win32-x86/jhdf5.dll";
	private static String WINDOWS_64 = "/natives/win32-x86_64/jhdf5.dll";
	private static String LINUX_32 = "/natives/linux-x86/libjhdf5.so";
	private static String LINUX_64 = "/natives/linux-x86_64/libjhdf5.so";
	private static String MAC_32 = "/natives/mac-x86/libjhdf5.jnilib";
	private static String MAC_64 = "/natives/mac-x86_64/libjhdf5.jnilib";
	
	public static SetNatives getInstance(){
		if(natives == null){
			natives = new SetNatives();
		}
		
		return natives;
	}
	
	/**
	 * Sets the native at whatever environment it's using this jar. 
	 * 
	 * @param location - Location of folder where the native will be 
	 *                   stored.
	 * @throws IOException
	 */
	public void setHDF5Native(String location) throws IOException{
		//Check if property has been set
		if(System.getProperty("ncsa.hdf.hdf5lib.H5.hdf5lib")==null){
			//Get the native location on the resources
			String nativeLocation = getNativeLocation();
			
			//Get name of native file
			String[] locationSplit = nativeLocation.split("/");
			String name = "/"+locationSplit[locationSplit.length-1];
			
			//create local file at specified location
			File targetFile = new File(location+name);	
			targetFile.createNewFile();

			//Get inputstream with native from resources
			InputStream initialStream = this.getClass().getResourceAsStream(nativeLocation);
			OutputStream outStream = new FileOutputStream(targetFile);

			//use input stream to copy to target file (local native file)
			byte[] buffer = new byte[8 * 1024];
			int bytesRead;
			while ((bytesRead = initialStream.read(buffer)) != -1) {
				outStream.write(buffer, 0, bytesRead);
			}
			
			outStream.close();
			
			_logger.info("Setting hdf5 native " + targetFile.getPath());
			
			//sets the property pointing to native file created in local environment
			System.setProperty( "ncsa.hdf.hdf5lib.H5.hdf5lib", targetFile.getPath() );
		}
	}

	/**
	 * Get native library location for hdf5 
	 * 
	 * @return
	 */
	private String getNativeLocation() {
		String nativeLocation = null;
		
		if(this.isWindowsBasedPlatform()){
			nativeLocation = this.getWindowsLocation();
		}else if(this.isLinuxBasedPlatform()){
			nativeLocation = this.getLinuxLocation();
		}else if(this.isMacBasedPlatform()){
			nativeLocation = this.getMacLocation();
		}
		
		return nativeLocation;
	}
	
	/**
	 * Get location of windows native
	 * 
	 * @return
	 */
	private String getWindowsLocation() {
		String nativeLocation =null;
		boolean is64bit = (System.getenv("ProgramFiles(x86)") != null);

		if(is64bit){
			nativeLocation = WINDOWS_64;
		}
		else{
			nativeLocation = WINDOWS_32;
		}
		
		return nativeLocation;
	}
	
	/**
	 * Get location of linux native 
	 * 
	 * @return
	 */
	private String getLinuxLocation() {
		String nativeLocation =null;
		boolean is64bit = (System.getenv("ProgramFiles(x86)") != null);

		if(is64bit){
			nativeLocation = LINUX_64;
		}
		else{
			nativeLocation = LINUX_32;
		}
		
		return nativeLocation;
	}
	
	/**
	 * Get location of Mac native
	 * 
	 * @return
	 */
	private String getMacLocation() {
		String nativeLocation =null;
		boolean is64bit = (System.getenv("ProgramFiles(x86)") != null);

		if(is64bit){
			nativeLocation = MAC_64;
		}
		else{
			nativeLocation = MAC_32;
		}
		
		return nativeLocation;
	}

	/**
	 * Returns true is OS is Windows
	 * @return
	 */
	public boolean isWindowsBasedPlatform()
    {
        return System.getProperty("os.name").contains("Windows");
    }
    
	/**
	 * Returns true if OS is Linux
	 * @return
	 */
    public boolean isLinuxBasedPlatform()
    {
        return System.getProperty("os.name").toLowerCase().indexOf("nix") >= 0 ||
            System.getProperty("os.name").toLowerCase().indexOf("linux") >= 0;
    }


    /**
     * Returns true if OS is Mac
     * @return
     */
    public boolean isMacBasedPlatform()
    {
        if (isWindowsBasedPlatform()) return false;
        if (isLinuxBasedPlatform()) return false;

        return System.getProperty("os.name").toLowerCase().indexOf("mac") >= 0;
    }
}