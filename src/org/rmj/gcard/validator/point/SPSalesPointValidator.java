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
public class SPSalesPointValidator implements GCardPoint{
   private GRider poRider;
   private GEntity poData;
   private double pnTranTotl;
   private long pnPntEarnx;
   private String psMessage;

   
   public SPSalesPointValidator(){
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
      String date = SQLUtil.dateFormat((Date)poData.getValue("dTransact"), "yyyy-MM-dd");
      String refer = (String)poData.getValue("sSourceNo");
      
      //Make sure that the Sales is printed....
      String lsSQL = "SELECT nTranTotl"  + 
                    " FROM SP_SO_Master" + 
                    " WHERE sTransNox LIKE " + SQLUtil.toSQL(branch + "%") +
                      " AND dTransact = " + SQLUtil.toSQL(date) + 
                      " AND sSalesInv = " + SQLUtil.toSQL(refer) + 
                      " AND IFNULL(sReferNox, '') = ''" +
                      " AND cTranStat IN ('1', '2')";
      
      System.out.println(lsSQL);
      ResultSet loRS = poRider.executeQuery(lsSQL);
      
      if(loRS == null){
         this.psMessage = "Error loading source transaction...";
         this.pnTranTotl = 0;
         this.pnPntEarnx = 0;
      }
      else{
         try {
            if(loRS.next()){
               this.pnTranTotl = loRS.getDouble("nTranTotl");
               lsSQL = "SELECT nAmtPerPt, nMinPoint, nMaxPoint, nDiscount, nBonusPnt" +
                      " FROM G_Card_Points_Basis" +
                      " WHERE sSourceCd = " + SQLUtil.toSQL((String) poData.getValue("sSourceCd"));
               ResultSet point = poRider.executeQuery(lsSQL);

               if(point == null){
                  this.psMessage = "Error loading source transaction...";
                  this.pnTranTotl = 0;
                  this.pnPntEarnx = 0;
               }
               else{
                  if(point.next()){
                     this.psMessage = "";
                     this.pnPntEarnx = (long)(this.pnTranTotl / point.getDouble("nAmtPerPt"));
                  }
                  else{
                     this.psMessage = "Transaction type not found...";
                     this.pnPntEarnx = 0;
                  }
               }
            }
            
            //mac 2019.07.27
            //  requested by ate she
            //  i'll just ignore if the refer no is not found, just catch it before saving
            /*else{
               this.psMessage = "Source transaction not found...";
               this.pnTranTotl = 0;
               this.pnPntEarnx = 0;
            }*/
         } catch (SQLException ex) {
            ex.printStackTrace();
            this.pnTranTotl = 0;
            this.pnPntEarnx = 0;
         }
      }
   }

   @Override
   public boolean SaveOthers() {
      return true;
   }

   @Override
   public boolean CancelOthers() {
      return true;
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
      return psMessage;
   }
   
    @Override
    public double getTotalAmount() {
        return this.pnTranTotl;
    }
}
