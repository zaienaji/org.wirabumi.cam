package org.wirabumi.cam.process;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;
import org.wirabumi.cam.AssetPrint;
import org.wirabumi.cam.AssetPrintLine;
import org.wirabumi.gen.oez.utility.RawPrintUtility;


public class PrintAssetBarcode extends DalBaseProcess {
	Logger log4j = Logger.getLogger(PrintAssetBarcode.class);
	private final int max = 12;
	
	public String getAssetRawTextPrint(String documentID) throws Exception {
		
		AssetPrint assetprint = OBDal.getInstance().get(AssetPrint.class, documentID);
		if (assetprint==null)
			throw new OBException(documentID+" is not valid asset print id");
		
		List<AssetPrintLine> apLineList = assetprint.getCamAssetprintlineList();
		if (apLineList==null || apLineList.size()==0)
			throw new OBException("asset print"+assetprint.getDocumentNo()+" does not have any lines to print");
		
		//read asset key and name
		List<String> assetNameList = new ArrayList<String>();
		List<String> assetKeyList = new ArrayList<String>();
		
		for (AssetPrintLine apLine : apLineList){
			Asset asset = apLine.getAsset();
			
			String assetkey = asset.getSearchKey();
			assetKeyList.add(assetkey);
			
			String assetname = asset.getName();
			assetNameList.add(assetkey);
			
		}
		
		StringBuilder sb = new StringBuilder();
		//print setting
		//^XA --> start command
		//~TA --> tear off position
		//~JSN --> ? //TODO
		//^LT --> lable top
		//^MNW --> media tracking, kenapa W? //TODO
		//^MTT --> media type, thermal transfer
		//^PON --> print orientation, normal
		//^PMN --> print mirror, no
		//^LH0,0 --> label home, 0,0s
		//^JMA --> milimeter, 24 dot/mm
		//^PR5 --> print rate (max: 5), slew rate (max 5), backfeed rate (max 5)
		//~SD --> printing darkness 15, max 30
		//^LRN --> reversed print, no
		//^CI0 --> ? //TODO
		sb.append("^XA~TA000~JSN^LT0^MNW^MTT^PON^PMN^LH0,0^JMA^PR5,5~SD15^LRN^CI0");
		sb.append("^XA");//start command
		sb.append("^MMT");//print mode --> tear off
		sb.append("^PW663");//print width
		sb.append("^LL0168");//label lenght
		sb.append("^LS0");//labelshift? //TODO
		sb.append("^JUS");//save current setting
		sb.append("^XZ");//end command
		sb.append((char)10);//feed new line
		
		//print barcode
		for (int i=0; i<assetKeyList.size(); i++){
			//start command on each row
			sb.append("^XA^PMN");
			sb.append((char)10);
			
			//^FT523,165-->^FT --> field typeset; 523-->field di print di koordinat x=523; 165-->field di print di koordinat y=165
			//^BQN,2,6 artinya ^BQ --> qr code; N-->normal; 2-->enhanced; 6-->6--dpi printer
			
			//asset kanan
			sb.append(buildAssetCodeToPrintKanan(assetKeyList.get(i), assetNameList.get(i)));
			
			if ((i+1)>=assetKeyList.size()){
				sb.append("^PQ1,0,1,Y^XZ");sb.append((char)10);
				break;
			}
				
			i++;
			
			//asset kiri
			sb.append(buildAssetCodeToPrintKiri(assetKeyList.get(i), assetNameList.get(i)));
			
			//end command on each row
			//^PQ print quality; 1--> total quantity label to print, 0--> pause and cut value (no pause), 1-->replicate 1x of each serial number
			//Y-->override pause count (Y)
			//^XZ-->end of file
			sb.append("^PQ1,0,1,Y^XZ");sb.append((char)10);
			
		}
		
		return sb.toString();
	}
	
	private String buildAssetCodeToPrintKanan(String assetcode, String assetname){
		/*
		 * ini barcode di kanan
		 * 192 adalah batas kiri label. makin kecil angkanya makin ke kanan
		 * 30 adalah batas bawah label. makin kecil angkanya makin ke bawah
		 * lable paling banyak 15 karakter
		 * 
		 * BARIS 1: 192, 129
		 * BARIS 2: 192, 149
		 */
		
		StringBuilder sb = new StringBuilder();
		
		int length = assetname.length();
		int ekor = 0;
		int kepala = max;
		int i=0;
		while(true){
			String labelText =null;
			if (kepala<=length)
				labelText = assetname.substring(ekor, kepala);
			else
				labelText = assetname.substring(ekor, length);
			
			switch (i) {
			case 0:
				sb.append("^FT178,129^A0I,19,26^FH").append((char)92).append("^FD").append(labelText).append("^FS");sb.append((char)10);
				
				break;
				
			case 1:
				sb.append("^FT178,109^A0I,19,26^FH").append((char)92).append("^FD").append(labelText).append("^FS");sb.append((char)10);
				break;
				
			case 2:
				sb.append("^FT178,89^A0I,19,26^FH").append((char)92).append("^FD").append(labelText).append("^FS");sb.append((char)10);
				break;
				
			case 3:
				sb.append("^FT178,69^A0I,19,26^FH").append((char)92).append("^FD").append(labelText).append("^FS");sb.append((char)10);
				break;
				
			case 4:
				sb.append("^FT178,49^A0I,19,26^FH").append((char)92).append("^FD").append(labelText).append("^FS");sb.append((char)10);
				break;
				
			case 5:
				sb.append("^FT178,29^A0I,19,26^FH").append((char)92).append("^FD").append(labelText).append("^FS");sb.append((char)10);
				break;

			default:
				kepala=length;
				break;
			}
			
			if (kepala>=length)
				break;
			
			i++;
			ekor +=max;
			kepala +=max;
			
		}
		
		if (assetcode.length()<25){
			sb.append("^FT183,175^BQN,2,6");sb.append((char)10);
		} else{
			sb.append("^FT183,175^BQN,2,5");sb.append((char)10);
		}
				
		sb.append("^FDLA,").append(assetcode).append("^FS");sb.append((char)10);
		
		return sb.toString();
		
	}
	
	private String buildAssetCodeToPrintKiri(String assetcode, String assetname){
		/*
		 * ini barcode di kanan
		 * 192 adalah batas kiri label. makin kecil angkanya makin ke kanan
		 * 30 adalah batas bawah label. makin kecil angkanya makin ke bawah
		 * lable paling banyak 15 karakter
		 * 
		 * BARIS 1: 192, 129
		 * BARIS 2: 192, 149
		 */
		
		StringBuilder sb = new StringBuilder();
		
		int length = assetname.length();
		int ekor = 0;
		int kepala = max;
		int i=0;
		while(true){
			String labelText =null;
			if (kepala<=length)
				labelText = assetname.substring(ekor, kepala);
			else
				labelText = assetname.substring(ekor, length);
			
			switch (i) {
			case 0:
				sb.append("^FT515,129^A0I,19,26^FH").append((char)92).append("^FD").append(labelText).append("^FS");sb.append((char)10);
				break;
				
			case 1:
				sb.append("^FT515,109^A0I,19,26^FH").append((char)92).append("^FD").append(labelText).append("^FS");sb.append((char)10);
				break;
				
			case 2:
				sb.append("^FT515,89^A0I,19,26^FH").append((char)92).append("^FD").append(labelText).append("^FS");sb.append((char)10);
				break;
				
			case 3:
				sb.append("^FT515,69^A0I,19,26^FH").append((char)92).append("^FD").append(labelText).append("^FS");sb.append((char)10);
				break;
				
			case 4:
				sb.append("^FT515,49^A0I,19,26^FH").append((char)92).append("^FD").append(labelText).append("^FS");sb.append((char)10);
				break;
				
			case 5:
				sb.append("^FT515,29^A0I,19,26^FH").append((char)92).append("^FD").append(labelText).append("^FS");sb.append((char)10);
				break;

			default:
				kepala=length;
				break;
			}
			
			if (kepala>=length)
				break;
			
			i++;
			ekor +=max;
			kepala +=max;
		}
		
		if (assetcode.length()<25){
			sb.append("^FT518,180^BQN,2,6");sb.append((char)10);
		} else {
			sb.append("^FT518,180^BQN,2,5");sb.append((char)10);
		}
		sb.append("^FDLA,").append(assetcode).append("^FS");sb.append((char)10);
		
		return sb.toString();
	}
	
	public String getServletInfo() {
		return "Servlet that print asset barcode";
	}
	@Override
	protected void doExecute(ProcessBundle bundle) throws Exception {
		HttpServletRequest request = RequestContext.get().getRequest();
		VariablesSecureApp vars = new VariablesSecureApp(request);
		String printerAddress = Utility.getPreference(vars, "RAWTEXTPRINTERADDRESS", null);
		String printerPort = Utility.getPreference(vars, "RAWTEXTPRINTERPORT", null);
		
		String documentID = (String) bundle.getParams().get("CAM_Assetprint_ID");
		if (documentID==null || documentID.isEmpty())
			throw new OBException("movement id not found");
		documentID=documentID.replace("(", "");
		documentID=documentID.replace(")", "");
		documentID=documentID.replace("\'", "");
		
		try {
			//print asset to barcode printer
			String textToPrint = getAssetRawTextPrint(documentID);
			
			// print to socket (for production)
			RawPrintUtility.sendToPrint(textToPrint, printerAddress, printerPort);
			
		} catch (Exception e) {
			throw new OBException(e.getMessage());
		}
		
	} 
		 
}
