/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rmj.gcard.validator.reward;

import org.rmj.appdriver.GRider;
import org.rmj.appdriver.iface.GEntity;

/**
 *
 * @author kalyptus
 */
public interface GCardReward {
   public void setGRider(GRider poRider);
   public void setData(GEntity poData);
   public boolean CheckSource();
   public boolean SaveOthers();
   public boolean CancelOthers();
   public boolean isSaveValid();
   public boolean isCancelValid();
   public String getMessage();
}
