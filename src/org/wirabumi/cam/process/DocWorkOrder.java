package org.wirabumi.cam.process;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.data.FieldProvider;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.ad_forms.Account;
import org.openbravo.erpCommon.ad_forms.AcctSchema;
import org.openbravo.erpCommon.ad_forms.AcctServer;
import org.openbravo.erpCommon.ad_forms.DocLine;
import org.openbravo.erpCommon.ad_forms.Fact;
import org.openbravo.erpCommon.ad_forms.FactLine;
import org.openbravo.erpCommon.utility.SequenceIdData;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.financialmgmt.accounting.coa.AccountingCombination;
import org.openbravo.model.financialmgmt.accounting.coa.ElementValue;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.openbravo.model.financialmgmt.assetmgmt.AssetAccounts;
import org.wirabumi.cam.WorkOrderAsset;

public class DocWorkOrder extends AcctServer {
	
	static Logger log4jDocWorkOrder = Logger.getLogger(DocWorkOrder.class);
	private String SeqNo = "0";

	@Override
	public void loadObjectFieldProvider(ConnectionProvider conn, String AD_Client_ID, String Id)
			throws ServletException {
		setObjectFieldProvider(DocWorkOrderData.select(conn, AD_Client_ID, Id));

	}

	@Override
	public boolean loadDocumentDetails(FieldProvider[] data, ConnectionProvider conn) {
		/*
		 * dipakai untuk mengisi
		 * 	DocumentType = AcctServer.DOCTYPE_MatInventory;
		 * 	C_Currency_ID = NO_CURRENCY;
		 * 	DateDoc = data[0].getField("MovementDate");
		 * 	loadDocumentType(); // lines require doc type
		 * 	Contained Objects:
		 * 		p_lines = loadLines(conn);
		 * 	setelah itu return true;
		 */
		DocumentType = "CAM_WO";
		C_Currency_ID = NO_CURRENCY;
		DateDoc = data[0].getField("movementdate");
		loadDocumentType();
		
		//Contained Objects:
		p_lines = loadLines(conn);
		return true;
	}

	private DocLine[] loadLines(ConnectionProvider conn) {
		ArrayList<Object> list = new ArrayList<Object>();
	    DocLineWorkOrderData[] data = null;
	    OBContext.setAdminMode(false);
	    try {
	      data = DocLineWorkOrderData.select(conn, Record_ID);
	      for (int i = 0; i < data.length; i++) {
	        String Line_ID = data[i].getField("cam_workorderasset_id");
	        String assetID = data[i].getField("a_asset_id");
	        String costcenterID = data[i].getField("c_costcenter_id");
	        DocLine docLine = new DocLine(DocumentType, Record_ID, Line_ID);
	        docLine.m_A_Asset_ID=assetID;
	        docLine.m_C_Costcenter_ID=costcenterID;
	        docLine.loadAttributes(data[i], this);
	        list.add(docLine);
	      }
	    } catch (ServletException e) {
	    	log4jDocWorkOrder.warn(e);
	    } finally {
	      OBContext.restorePreviousMode();
	    }
	    // Return Array
	    DocLine[] dl = new DocLine[list.size()];
	    list.toArray(dl);
	    return dl;
	}

	@Override
	public BigDecimal getBalance() {
		return BigDecimal.ZERO;
	}

	@Override
	public Fact createFact(AcctSchema as, ConnectionProvider conn, Connection con, VariablesSecureApp vars)
			throws ServletException {
		org.openbravo.model.financialmgmt.accounting.coa.AcctSchema accountingSchema = 
				OBDal.getInstance().get(org.openbravo.model.financialmgmt.accounting.coa.AcctSchema.class, as.m_C_AcctSchema_ID);
		// membuat object Fact as we know it, yang didalamnya ada array factLine.
		// dalam membuat factLine, dilakukan dengan memanggil method createLine.
		
		// Select specific definition
	    String strClassname =  DocWorkOrderData
	        .selectTemplateDoc(conn, as.m_C_AcctSchema_ID, DocumentType);
	    if (strClassname.equals(""))
	      strClassname = DocWorkOrderData.selectTemplate(conn, as.m_C_AcctSchema_ID, AD_Table_ID);
	    if (!strClassname.equals("")) {
	      try {
	        DocWorkOrderTemplate newTemplate = (DocWorkOrderTemplate) Class.forName(strClassname)
	            .newInstance();
	        return newTemplate.createFact(this, as, conn, con, vars);
	      } catch (Exception e) {
	    	  log4jDocWorkOrder.error("Error while creating new instance for DocInventoryTemplate - " + e);
	      }
	    }
		
	    C_Currency_ID = as.getC_Currency_ID();
	    
	    AccountingCombination acFixedAsset = getFixedAssetAccountCombination(vars);
	    
	    // create Fact Header
	    Fact fact = new Fact(this, as, Fact.POST_Actual);
	    String Fact_Acct_Group_ID = SequenceIdData.getUUID();
	    
	    // Line pointers
	    FactLine dr = null;
	    FactLine cr = null;
	    log4jDocWorkOrder.debug("CreateFact - before loop");
	    if (p_lines.length == 0) {
	      setStatus(STATUS_DocumentDisabled);
	    }
	    int validLines=0;
	    for (int i = 0; i < p_lines.length; i++) {
	      DocLine line = p_lines[i];
	      WorkOrderAsset woA = OBDal.getInstance().get(WorkOrderAsset.class, line.m_TrxLine_ID);
	      if (woA==null)
	    	  continue; //invalid work order asset record;
	      
	      boolean supportedAction = woA.isDisposed() || woA.isAssetmovement();
	      if (!supportedAction)
	    	  continue; //sekarang ini hanya asset disposal dan movement saja yg di support.
	      
	      //do post logic here	      
	      Asset asset = woA.getAsset();
	      if (asset==null)
	    	  continue; //no asset to be processed;
	      line.m_A_Asset_ID=asset.getId();
	      Currency costCurrency = asset.getCurrency();
	      if (costCurrency==null)
	    	  continue; //has no currency
	      if (asset.isFullyDepreciated())
	    	  continue; //asset has no book value;
	      if (!asset.isDepreciate())
	    	  continue; //non financial asset;
	      BigDecimal hargaperolehan = asset.getAssetValue();
	      if (hargaperolehan==null || hargaperolehan.equals(BigDecimal.ZERO))
	    	  continue; //has no asset value;
	      BigDecimal akumulasidepresiasi = asset.getDepreciatedValue();
	      if (akumulasidepresiasi==null)
	    	  akumulasidepresiasi=BigDecimal.ZERO;
	      BigDecimal prevdepresiasi = asset.getPreviouslyDepreciatedAmt();
	      if (prevdepresiasi==null)
	    	  prevdepresiasi=BigDecimal.ZERO;
	      BigDecimal nilaibuku = hargaperolehan.subtract(akumulasidepresiasi).subtract(prevdepresiasi);
	      if (nilaibuku.equals(BigDecimal.ZERO))
	    	  continue; //have no book value;
	      
	      String costs = nilaibuku.toString();
	      BigDecimal b_Costs = nilaibuku;
	      OBCriteria<AssetAccounts> assetAccountingC = OBDal.getInstance().createCriteria(AssetAccounts.class);
	      assetAccountingC.add(Restrictions.eq(AssetAccounts.PROPERTY_ASSET, asset));
	      assetAccountingC.add(Restrictions.eq(AssetAccounts.PROPERTY_ACCOUNTINGSCHEMA, accountingSchema));
	      List<AssetAccounts> assetAccountingL = assetAccountingC.list();
	      if (assetAccountingL.size()==0){
	    	  Map<String, String> parameters = new HashMap<String, String>();
	    	  parameters.put("trx", woA.getIdentifier());
	    	  parameters.put("asset", asset.getIdentifier());
	    	  setMessageResult(conn, STATUS_InvalidAccount, "error", parameters);
	    	  continue; //no accounting configuration
	      }
	      AssetAccounts assetAccounting = assetAccountingL.get(0);
	      AccountingCombination acAkumulasiDepresiasi = assetAccounting.getAccumulatedDepreciation();
	      AccountingCombination acDisposalLoss = assetAccounting.getDisposalLoss();
	      
	      if (woA.isDisposed()) {
	    	  if (acAkumulasiDepresiasi==null || acDisposalLoss==null){
		    	  Map<String, String> parameters = new HashMap<String, String>();
		    	  parameters.put("trx", woA.getIdentifier());
		    	  parameters.put("asset", asset.getIdentifier());
		    	  setMessageResult(conn, STATUS_InvalidAccount, "error", parameters);
		    	  continue; //no accounting configuration
		      }
	    	  
	    	  log4jDocWorkOrder.debug("CreateFact - before DR - Costs: " + costs);
		      // Asset disposal loss DR
		      Account akunDisposalLoss = new Account(conn, acDisposalLoss.getId());
		      dr = fact.createLine(line, akunDisposalLoss, costCurrency.getId(), costs, Fact_Acct_Group_ID,
		          nextSeqNo(SeqNo), DocumentType, conn);
		      // may be zero difference - no line created.
		      if (dr == null) {
		        continue;
		      }
		      
		      log4jDocWorkOrder.debug("CreateFact - before CR");
		      // Asset accumulation depreciation CR
		      Account akunAkumulasiDepresiasi = new Account(conn, acAkumulasiDepresiasi.getId());
		      cr = fact.createLine(line, akunAkumulasiDepresiasi, costCurrency.getId(), (b_Costs.negate()).toString(),
		          Fact_Acct_Group_ID, nextSeqNo(SeqNo), DocumentType, conn);
		      if (cr == null) {
		        continue;
		      }
	      } else if (woA.isAssetmovement()) {
	    	  if (acAkumulasiDepresiasi==null || acFixedAsset==null){
		    	  Map<String, String> parameters = new HashMap<String, String>();
		    	  parameters.put("trx", woA.getIdentifier());
		    	  parameters.put("asset", asset.getIdentifier());
		    	  setMessageResult(conn, STATUS_InvalidAccount, "error", parameters);
		    	  continue; //no accounting configuration
		      }
	    	  
	    	  //dapatkan total akumulasi depresiasi dengan menjumlahkan semua akun akumulasi depresiasi dari fact acct
	    	  BigDecimal totalAkumulasiDepresiasi = BigDecimal.ZERO;
	    	  String sql = "select sum(amtacctcr-amtacctdr) as akumulasidepresiasi \n" + 
	    	  		"from fact_acct\n" + 
	    	  		"where account_id=?\n" + 
	    	  		"and a_asset_id=?";
	    	  Connection conn2 = OBDal.getInstance().getConnection();
	    	  PreparedStatement ps;
	    	  try {
	    		  ps = conn2.prepareStatement(sql);
	    		  ps.setString(1, acAkumulasiDepresiasi.getAccount().getId());
	    		  ps.setString(2, asset.getId());
	    		  ResultSet rs = ps.executeQuery();
	    		  while (rs.next()) {
	    			  totalAkumulasiDepresiasi=rs.getBigDecimal("akumulasidepresiasi");
	    			  break;
	    		  }
	    	  } catch (SQLException e) {
	    		  e.printStackTrace();
	    		  throw new OBException(e.getMessage());
	    	  }
			  
			  log4jDocWorkOrder.debug("get accumulated depreciation for asset id : " + asset.getId()+" accumulated depreciation account id: "+acAkumulasiDepresiasi.getAccount().getId());
	    	  
	    	  //pindahkan harga perolehan dan akumulasi depresiasi dari cost center lama ke cost center baru
	    	  //m_lines harus ada cost center baru dan asset
	    	  //harga perolehan diperoleh dari field asset value pada asset
	    	  //akumaulasi depresiasi diperoleh dari field depreciated value pada asset
	    	  //ayat jurnal:
	    	  //	1. fixed asset pada cost center baru (D)
	    	  //		2. akumulasi depresiasi pada cost center baru (K)
	    	  //	3. akumulasi depresiasi pada cost center lama (D)
	    	  //		4. fixed asset pada cost center lama (K)
	    	  
	    	  DocLine lineCostcenterLama = new DocLine(line.p_DocumentType, line.m_TrxHeader_ID, line.m_TrxLine_ID);
	    	  lineCostcenterLama.copyInfo(line);
	    	  if (woA.getOldCostCenter()!=null)
	    		  lineCostcenterLama.m_C_Costcenter_ID = woA.getOldCostCenter().getId();
	    	  else
	    		  lineCostcenterLama.m_C_Costcenter_ID = null;
	    	  
	    	  DocLine lineCostcenterBaru = new DocLine(line.p_DocumentType, line.m_TrxHeader_ID, line.m_TrxLine_ID);
	    	  lineCostcenterBaru.copyInfo(line);
	    	  if (woA.getCostCenter()!=null)
	    		  lineCostcenterBaru.m_C_Costcenter_ID=woA.getCostCenter().getId();
	    	  else
	    		  lineCostcenterBaru.m_C_Costcenter_ID=null;
	    	  
	    	  //ayat jurnal ke-1.
	    	  log4jDocWorkOrder.debug("CreateFact - harga perolehan di cost center baru DR: " + hargaperolehan);		      
		      Account akunHargaPerolehan = new Account(conn, acFixedAsset.getId());
		      dr = fact.createLine(lineCostcenterBaru, akunHargaPerolehan, costCurrency.getId(), hargaperolehan.toString(), Fact_Acct_Group_ID,
		          nextSeqNo(SeqNo), DocumentType, conn);
		      if (dr == null) {
		        continue;
		      }
		      
		      //ayat jurnal ke-2.
		      log4jDocWorkOrder.debug("CreateFact - akumulasi depresiasi di cost center baru DR: " + akumulasidepresiasi);
		      Account akunAkumulasiDepresiasi = new Account(conn, acAkumulasiDepresiasi.getId());
		      cr = fact.createLine(lineCostcenterBaru, akunAkumulasiDepresiasi, costCurrency.getId(), (totalAkumulasiDepresiasi.negate()).toString(),
		          Fact_Acct_Group_ID, nextSeqNo(SeqNo), DocumentType, conn);
		      if (cr == null) {
		        continue;
		      }
		      
		      //ayat jurnal ke-3.
		      cr = fact.createLine(lineCostcenterLama, akunAkumulasiDepresiasi, costCurrency.getId(), totalAkumulasiDepresiasi.toString(),
		          Fact_Acct_Group_ID, nextSeqNo(SeqNo), DocumentType, conn);
		      if (cr == null) {
		        continue;
		      }
		      
		      //ayat jurnal ke-4.
		      dr = fact.createLine(lineCostcenterLama, akunHargaPerolehan, costCurrency.getId(), hargaperolehan.negate().toString(), Fact_Acct_Group_ID,
		          nextSeqNo(SeqNo), DocumentType, conn);
		      if (dr == null) {
		        continue;
		      }
	      }
	      
	      validLines++;
	    }
	    
	    if (validLines==0) {
	      setStatus(STATUS_DocumentDisabled);
	    }
	    
	    log4jDocWorkOrder.debug("CreateFact - after loop");
	    SeqNo = "0";
	    return fact;
		
	}

	private AccountingCombination getFixedAssetAccountCombination(VariablesSecureApp vars) {
		//TODO seharusnya fix asset melekat di asset accounting, sementara pakai preference dan hardcoded.
		String fixedAssetAccountKey = Utility.getPreference(vars, "FixedAssetAccountKey", null);
		if (StringUtils.isEmpty(fixedAssetAccountKey))
			fixedAssetAccountKey="Fixed Asset";
		
		OBCriteria<ElementValue> evCriteria = OBDal.getInstance().createCriteria(ElementValue.class);
		evCriteria.add(Restrictions.eq(ElementValue.PROPERTY_NAME, fixedAssetAccountKey));
		evCriteria.add(Restrictions.eq(ElementValue.PROPERTY_ELEMENTLEVEL, "S"));
		evCriteria.setFilterOnReadableClients(true);
		evCriteria.setFilterOnReadableOrganization(true);
		List<ElementValue> evList = evCriteria.list();
		if (evList.size()==0)
			return null;
		
		List<AccountingCombination> acList = evList.get(0).getFinancialMgmtAccountingCombinationAccountList();
		if (acList.size()==0)
			return null;
		
		return acList.get(0);
		
	}

	@Override
	public boolean getDocumentConfirmation(ConnectionProvider conn, String strRecordId) {
		return true;
	}
	
	public String nextSeqNo(String oldSeqNo) {
		log4jDocWorkOrder.debug("DocInventory - oldSeqNo = " + oldSeqNo);
	    BigDecimal seqNo = new BigDecimal(oldSeqNo);
	    SeqNo = (seqNo.add(new BigDecimal("10"))).toString();
	    log4jDocWorkOrder.debug("DocInventory - nextSeqNo = " + SeqNo);
	    return SeqNo;
	}

}
