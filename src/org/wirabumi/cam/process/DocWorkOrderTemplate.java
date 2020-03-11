package org.wirabumi.cam.process;

import java.sql.Connection;

import javax.servlet.ServletException;

import org.apache.log4j.Logger;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.ad_forms.AcctSchema;
import org.openbravo.erpCommon.ad_forms.DocInventory;
import org.openbravo.erpCommon.ad_forms.DocInvoice;
import org.openbravo.erpCommon.ad_forms.Fact;

public abstract class DocWorkOrderTemplate {
	  static Logger log4jDocInventory = Logger.getLogger(DocInvoice.class);

	  /**
	   * Constructor
	   * 
	   */
	  public DocWorkOrderTemplate() {
	  }

	  /**
	   * Create Facts (the accounting logic) for MMI.
	   * 
	   * <pre>
	   *  Inventory
	   *      Inventory       DR      CR
	   *      InventoryDiff   DR      CR   (or Charge)
	   * </pre>
	   * 
	   * @param as
	   *          account schema
	   * @return Fact
	   */
	  public abstract Fact createFact(DocWorkOrder docWorkOrder, AcctSchema as,
	      ConnectionProvider conn, Connection con, VariablesSecureApp vars) throws ServletException;

	  public String getServletInfo() {
	    return "Servlet for the accounting";
	  } // end of getServletInfo() method
	}
