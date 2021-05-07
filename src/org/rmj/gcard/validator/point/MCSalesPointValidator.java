/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rmj.gcard.validator.point;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.iface.GEntity;
import org.rmj.appdriver.SQLUtil;

/**
 * @author kalyptus
 * @serial 201804130917
 */
public class MCSalesPointValidator implements GCardPoint{
   private GRider poRider;
   private GEntity poData;
   private long pnTranTotl;
   private long pnPntEarnx;
   private String psMessage;
   private String psTransNo;
   
   public MCSalesPointValidator(){
      this.poRider = null;
      this.poData = null;
      this.pnTranTotl = 0;
      this.pnPntEarnx = 0;
      this.psMessage = "";
   }
   
   @Override
   public void setGRider(GRider poRider) {
      this.poRider = poRider;
   }

   @Override
   public void setData(GEntity poData) {
      this.poData = poData;
   }
   
   @Override
   public void checkSource() {
      String branch = ((String)poData.getValue("sTransNox")).substring(0, 4);
      //MsgBox.showOk((String) poData.getValue("dTransact"));
      String date = SQLUtil.dateFormat((Date)poData.getValue("dTransact"), "yyyy-MM-dd");
      String refer = (String)poData.getValue("sSourceNo");
      
      String lsSQL = "SELECT b.sTransNox, b.sSerialID, b.sReplMCID" +  
                    " FROM MC_SO_Master a" +
                        ", MC_SO_Detail b" +
                    " WHERE a.sTransNox = b.sTransNox" +
                      " AND a.sTransNox LIKE " + SQLUtil.toSQL(branch + "%") +
                      " AND a.dTransact = " + SQLUtil.toSQL(date) + 
                      " AND a.sDRNoxxxx = " + SQLUtil.toSQL(refer) + 
                      " AND a.cTranStat <> '3'" +
                      " AND b.sSerialID <> ''" +
                      " AND b.cMotorNew = '1'";
      ResultSet loRS = poRider.executeQuery(lsSQL);
      System.out.println(lsSQL);
      this.psTransNo = "";

      if(loRS == null){
         this.psMessage = "checkSource: Error loading MC Sales transaction...";
         System.out.println(this.psMessage);
         this.pnTranTotl = 0;
         this.pnPntEarnx = 0;         
      }
      else{
         try {
            this.pnTranTotl = 0;
            while(loRS.next()){
               //Do not allow replacement to earn points...
               //If so how will this transaction get an FSEP for the MC?
               if(loRS.getString("sReplMCID").length() != 0){
                  this.psMessage = "checkSource: MC Sales is a replacement...";
                  System.out.println(this.psMessage);
                  this.pnTranTotl = 0;
                  this.pnPntEarnx = 0;    
                  this.psTransNo = "";
                  break;
               }
               else if(hasService(loRS.getString("sSerialID"))){
                  this.psMessage = "checkSource: Serial has FSEP record...";
                  System.out.println(this.psMessage);
                  this.pnTranTotl = 0;
                  this.pnPntEarnx = 0;    
                  this.psTransNo = "";
                  break;                  
               }
               else{
                  this.pnTranTotl = pnTranTotl + 1;
                  this.psTransNo = loRS.getString("sTransNox");
               }
            } //while(loRS.next()){
            
            //If no error was detected from validating the MC Sales then set points here...
            if(this.pnTranTotl > 0){
               lsSQL = "SELECT nAmtPerPt, nMinPoint, nMaxPoint, nDiscount, nBonusPnt" +
                      " FROM G_Card_Points_Basis" +
                      " WHERE sSourceCd = " + SQLUtil.toSQL((String) poData.getValue("sSourceCd"));
               ResultSet point = poRider.executeQuery(lsSQL);
               System.out.println(lsSQL);

               if(point == null){
                  this.psMessage = "checkSource: Error loading source transaction...";
                  System.out.println(this.psMessage);
                  this.pnTranTotl = 0;
                  this.pnPntEarnx = 0;
                  this.psTransNo = "";
               }
               else{
                  if(point.next()){
                     this.pnPntEarnx = (long)(pnTranTotl * point.getDouble("nMaxPoint"));
                  }
                  else{
                     this.psMessage = "checkSource: Points basis not found...";
                     System.out.println(this.psMessage);
                     this.pnPntEarnx = 0;
                     this.psTransNo = "";
                  }
               }
            }
         } catch (SQLException ex) {
            ex.printStackTrace();
            this.pnTranTotl = 0;
            this.pnPntEarnx = 0;         
            this.psTransNo = "";
         } //try {
      }
   }

   private boolean hasService(String fsSerialID){
      boolean bService = true;

      String lsSQL = "SELECT sSerialID"  + 
                    " FROM MC_Serial_Service" + 
                    " WHERE sSerialID = " + SQLUtil.toSQL(fsSerialID);
      try {
         ResultSet loRS = poRider.executeQuery(lsSQL);
         System.out.println(lsSQL);
         
         if(loRS == null){
            bService = true;
         }
         else{
            if(!loRS.next()){
               bService = false;
            }
         }
      } catch (SQLException ex) {
         ex.printStackTrace();
         bService = true;
      }
      return bService;
   } 

   
   @Override
   public boolean SaveOthers() {
      try {
         String lsSQL = "SELECT b.sSerialID, a.cPaymForm" +
                 " FROM MC_SO_Master a" +
                     ", MC_SO_Detail b" +
                 " WHERE a.sTransNox = b.sTransNox" + 
                   " AND a.sTransNox = " + SQLUtil.toSQL(this.psTransNo) +
                   " AND sSerialID <> ''" +
                   " AND cMotorNew = '1'";
         ResultSet loRS = poRider.executeQuery(lsSQL);
         System.out.println(lsSQL);

         if(loRS == null){
            this.psMessage = "SaveOthers: Error loading the MC Sales transaction...";
            return false;
         }
         
         if(!loRS.next()){
            this.psMessage = "SaveOthers: Unable to find the MC Sales transaction...";
            return false;
         }
         
         //Create MC_Serial_Service record for each valid serial
         do{
            int lnYellow = 2;
            int lnWhite = 0;
            
            if(loRS.getString("cPaymForm").compareToIgnoreCase("2") == 0)
               lnWhite = 10;
            else
               lnWhite = 5;
            
            String lsService = "INSERT INTO MC_Serial_Service(sSerialID, sGCardNox, nYellowxx, nWhitexxx, dTransact, cRecdStat)" +
                    " VALUES( " + SQLUtil.toSQL(loRS.getString("sSerialID")) +
                    ", " + SQLUtil.toSQL((String)poData.getValue("")) +
                    ", " + SQLUtil.toSQL(lnYellow) +
                    ", " + SQLUtil.toSQL(lnWhite) +
                    ", " + SQLUtil.toSQL(SQLUtil.dateFormat((Date)poData.getValue("dTransact"), "yyyy-MM-dd")) +
                    ", " + SQLUtil.toSQL("0") + ")";
            poRider.executeQuery(lsService, "MC_Serial_Service", "", "");
            
            if(poRider.getErrMsg().length() > 0){
               this.psMessage = "SaveOthers: Error inserting the MC_Serial_Service record...";
               return false;
            }
         }while(loRS.next());
         
         return true;
      } catch (SQLException ex) {
         ex.printStackTrace();
         return false;
      }
   }

   @Override
   public boolean CancelOthers() {
      try {
         String lsSQL = "SELECT a.sSerialID, IFNULL(b.sGCardNox, '') sGCardNox" +
                 " FROM MC_SO_Detail a" +
                     "  LEFT JOIN MC_Serial_Service b ON b.sSerialID = c.sSerialID" +
                 " WHERE a.sTransNox = " + SQLUtil.toSQL(this.psTransNo) +
                   " AND sSerialID <> ''" +
                   " AND cMotorNew = '1'";
         ResultSet loRS = poRider.executeQuery(lsSQL);
         System.out.println(lsSQL);

         if(loRS == null){
            this.psMessage = "CancelOthers: Error loading the MC Sales transaction...";
            return false;
         }
         
         if(!loRS.next()){
            this.psMessage = "CancelOthers: Unable to find the MC Sales transaction...";
            return false;
         }
         
         //Create MC_Serial_Service record for each valid serial
         do{
            String card = (String) poData.getValue("sGCardNox");
            if(loRS.getString("sGCardNox").compareToIgnoreCase(card) != 0)
            {
               this.psMessage = "CancelOthers: Stored GCard is different in the MC_Serial_Service...";
               return false;
            }
            
            lsSQL = "DELETE FROM MC_Serial_Service" + 
                   " WHERE sSerialID = " + SQLUtil.toSQL(loRS.getString("sSerialID")) + 
                     " AND sGCardNox = " + SQLUtil.toSQL(card);
            poRider.executeQuery(lsSQL, "MC_Serial_Service", "", "");
            
            if(poRider.getErrMsg().length() > 0){
               this.psMessage = "CancelOthers: Unable to find the MC_Serial_Service during deletion...";
               return false;
            }
            
         }while(loRS.next());
         
         return true;
      } catch (SQLException ex) {
         ex.printStackTrace();
         return false;
      }
   }

   @Override
   public boolean isSaveValid() {
      return true;
   }

   @Override
   public boolean isCancelValid() {
      return true;
   }

   @Override
   public long getPoints() {
      return this.pnPntEarnx;
   }

   @Override
   public String getMessage() {
      return this.psMessage;
   }

    @Override
    public double getTotalAmount() {
        return this.pnTranTotl;
    }
}
