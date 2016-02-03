package com.exlibris.dps.delivery.vpp.jpeg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.exlibris.core.infra.common.util.StringUtils;
import com.exlibris.core.sdk.formatting.DublinCore;
import com.exlibris.core.sdk.parser.IEParserException;
import com.exlibris.digitool.common.dnx.DnxDocument;
import com.exlibris.digitool.common.streams.ScriptUtil;
import com.exlibris.dps.sdk.access.Access;
import com.exlibris.dps.sdk.access.AccessException;
import com.exlibris.dps.sdk.delivery.AbstractViewerPreProcessor;
import com.exlibris.dps.sdk.delivery.SmartFilePath;
import com.exlibris.dps.sdk.deposit.IEParser;


public class CustomizeJpgVppWaterMark extends AbstractViewerPreProcessor{

	private static final String CONVERT = "convert";
	private String logoPath=null;
	//Default "rights" value
	private String TEXT = "All rights reserved";
	private String filePid = null;
	Access access;

	//This method will be called by the delivery framework before the call for the execute Method
	@Override
	public void init(DnxDocument dnx, Map<String, String> viewContext, HttpServletRequest request, String dvs,String ieParentId, String repDirName)
			throws AccessException {
		super.init(dnx, viewContext, request, dvs, ieParentId, repDirName);
        this.filePid = getPid();
	}
	
	public void initParams(Map<String, String> initParams) {
		if(!StringUtils.isEmptyString(initParams.get("logoFilePath"))){
			logoPath = initParams.get("logoFilePath").trim();
		}
  }

	public boolean runASync(){
		return false;
	}

	//Does the pre-viewer processing tasks.
	public void execute() throws Exception {

		convertFile();

		//Set the Delivery Access with parameters the JPG viewer will need.
        Map<String, Object> paramMap = getAccess().getParametersByDVS(getDvs());
        paramMap.put("file_pid", filePid);
        paramMap.put("rep_pid", repDirName);
        paramMap.putAll(getViewContext());
        getAccess().setParametersByDVS(getDvs(), paramMap);
	}

	private void convertFile() throws Exception{

		String dnxDocument = getAccess().getFileInfoByDVS(dvs, filePid).toString();

		//STEP 1: Export the file to a temp directory so we can modify it for delivery
		String filePath = getAccess().exportFileStream(filePid, CustomizeJpgVppWaterMark.class.getSimpleName(), ieParentId, repDirName, null, dnxDocument, getDvs());

		//STEP 2: Set the Delivery Access with the exported file path in order to allow the JPG Viewer to use the modified file.
		getAccess().setFilePathByDVS(getDvs(), new SmartFilePath(filePath), filePid);

		//STEP 3: Get text from dc:rights field:
		String dcRights= getDCRecord().getDcValue("rights");
		if (dcRights != null) {
		TEXT = dcRights;
		}

		//STEP 4: create and run the ImageMagick Script:
		//Create an array of arguments for the script
		String COMMAND = filePath + " xc:none -composite -compose bumpmap -gravity center " + logoPath + " -fill Black -composite -gravity southeast -annotate 0x0+50+20 " + TEXT + " " +filePath ;

		List<String> list = new ArrayList<String>(Arrays.asList(COMMAND.split(" ")));
		List<String> args = new ArrayList<String>();
				
		for (String item : list) {
			args.add(item);
		}
		
		try {
        	//run the script using the Rosetta ScriptUtil
            ScriptUtil.runScript(CONVERT, args);
        } catch (Exception e) {
        	System.err.println("Failed converting file: " + filePid);
        	e.printStackTrace();
        }
	}

	private DublinCore getDCRecord() throws Exception, IEParserException {
              IEParser ieParser = getAccess().getIEByDVS(dvs);
              DublinCore dc = ieParser.getIeDublinCore();
              return dc;
	}
}