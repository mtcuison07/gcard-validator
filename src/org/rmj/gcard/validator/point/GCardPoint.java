/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rmj.gcard.validator.point;

/**
 *
 * @author kalyptus
 */

import org.rmj.appdriver.GRider;
import org.rmj.appdriver.iface.GEntity;

public interface GCardPoint {
   public void setGRider(GRider poRider);
   public void setData(GEntity poData);
   public void checkSource();
   public long getPoints();
   public double getTotalAmount();
   public boolean SaveOthers();
   public boolean CancelOthers();
   public boolean isSaveValid();
   public boolean isCancelValid();
   public String getMessage();
}
