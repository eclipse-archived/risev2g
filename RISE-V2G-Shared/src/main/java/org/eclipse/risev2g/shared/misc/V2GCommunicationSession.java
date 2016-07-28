/*******************************************************************************
 *  Copyright (c) 2016 Dr.-Ing. Marc Mültin.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *    Dr.-Ing. Marc Mültin - initial API and implementation and initial documentation
 *******************************************************************************/
package org.eclipse.risev2g.shared.misc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Observable;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.eclipse.risev2g.shared.enumerations.V2GMessages;
import org.eclipse.risev2g.shared.messageHandling.MessageHandler;
import org.eclipse.risev2g.shared.messageHandling.PauseSession;
import org.eclipse.risev2g.shared.messageHandling.TerminateSession;
import org.eclipse.risev2g.shared.utils.ByteUtils;
import org.eclipse.risev2g.shared.utils.MiscUtils;
import org.eclipse.risev2g.shared.utils.SecurityUtils;
import org.eclipse.risev2g.shared.v2gMessages.msgDef.EnergyTransferModeType;
import org.eclipse.risev2g.shared.v2gMessages.msgDef.PaymentOptionListType;
import org.eclipse.risev2g.shared.v2gMessages.msgDef.PaymentOptionType;
import org.eclipse.risev2g.shared.v2gMessages.msgDef.V2GMessage;

public abstract class V2GCommunicationSession extends Observable {

	private Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private HashMap<V2GMessages, State> states;
	private State currentState;
	private State startState;
	private MessageHandler messageHandler;
	private byte[] sessionID;
	private V2GTPMessage v2gTpMessage;
	private V2GMessage v2gMessage;
	private boolean secureCommunication;
	
	public V2GCommunicationSession() {
		setStates(new HashMap<V2GMessages, State>());
		setMessageHandler(new MessageHandler(this));
		setSessionID(null);
		setV2gTpMessage(null);
	}
	
	
	/**
	 * Generates a session ID (with length of 8 bytes) from a given long value.
	 * @param The long value representing a session ID (either 0 or a previously stored session ID)
	 * @return The byte array representation of the provided session ID
	 */
	public byte[] generateSessionIDFromValue(long fromValue) {
		return ByteUtils.toByteArrayFromLong(fromValue);
	}
	
	/**
	 * Generates randomly a new session ID (with length of 8 bytes) and takes care that the newly generated 
	 * session ID does not match the store previous session ID and that it is unequal to 0.
	 * @return The byte array representation of the provided session ID
	 */
	public byte[] generateSessionIDRandomly() {
		byte[] sessionID = new byte[8];
		
		while (sessionID == null || ByteUtils.toLongFromByteArray(sessionID) == 0L || Arrays.equals(sessionID, getSessionID())) {
			sessionID = SecurityUtils.generateRandomNumber(8);
		}
	
		return sessionID;
	}
	
	protected void pauseSession(PauseSession pauseObject) {
		getLogger().info("Pausing"
				+ " V2G communication session");
		setChanged();
		notifyObservers(pauseObject);
	}
	
	
	protected void terminateSession(TerminateSession termination) {
		String terminationPrefix = "Terminating V2G communication session, reason: ";
		
		if (termination.isSuccessfulTermination()) {
			getLogger().info(terminationPrefix + termination.getReasonForSessionStop());
		} else {
			getLogger().warn(terminationPrefix + termination.getReasonForSessionStop());
		}
		
		setChanged();
		notifyObservers(termination);
	}
	
	/**
	 * Should be used if no TerminateSession instance has been provided by the respective state 
	 * but some other case causes a session termination
	 * 
	 * @param reason The termination cause
	 * @param successful True, if in case of a successful session termination, false otherwise
	 */
	protected void terminateSession(String reason, boolean successful) {
		String terminationPrefix = "Terminating V2G communication session, reason: "; 
		
		TerminateSession termination = new TerminateSession(reason, successful);
		if (successful)	getLogger().debug(terminationPrefix + reason);
		else getLogger().error(terminationPrefix + reason);
		
		setChanged();
		notifyObservers(termination);
	}

	
	public PaymentOptionListType getPaymentOptions() {
		@SuppressWarnings("unchecked")
		ArrayList<PaymentOptionType> paymentOptions = (ArrayList<PaymentOptionType>) (MiscUtils.getPropertyValue("SupportedPaymentOptions"));
		
		if (paymentOptions == null) {
			paymentOptions = new ArrayList<PaymentOptionType>();
		}
		
		PaymentOptionListType paymentOptionList = new PaymentOptionListType();
		paymentOptionList.getPaymentOption().addAll(paymentOptions);
		
		return paymentOptionList;
	}
	
	
	public ArrayList<EnergyTransferModeType> getSupportedEnergyTransferModes() {
		@SuppressWarnings("unchecked")
		ArrayList<EnergyTransferModeType> energyTransferModes = 
				(MiscUtils.getPropertyValue("SupportedEnergyTransferModes") != null) ?
				((ArrayList<EnergyTransferModeType>) MiscUtils.getPropertyValue("SupportedEnergyTransferModes")) :
				new ArrayList<EnergyTransferModeType>();
		
		return energyTransferModes;
	}
	

	public Logger getLogger() {
		return logger;
	}

	public HashMap<V2GMessages, State> getStates() {
		return states;
	}

	public void setStates(HashMap<V2GMessages, State> states) {
		this.states = states;
	}

	public State getCurrentState() {
		return currentState;
	}

	public void setCurrentState(State newState) {
		this.currentState = newState;
		if (newState == null) {
			getLogger().error("New state is not provided (null)");
		} else {
			getLogger().debug("New state is " + this.currentState.getClass().getSimpleName());
		}
	}
	
	public State getStartState() {
		return startState;
	}

	public void setStartState(State startState) {
		this.startState = startState;
	}

	public MessageHandler getMessageHandler() {
		return messageHandler;
	}

	public byte[] getSessionID() {
		return sessionID;
	}

	public void setSessionID(byte[] sessionID) {
		if (sessionID == null) {
			sessionID = generateSessionIDFromValue(0L);
		} 
		this.sessionID = sessionID;
		MiscUtils.getV2gEntityConfig().setProperty("SessionID", String.valueOf(ByteUtils.toLongFromByteArray(sessionID)));
	}

	public V2GTPMessage getV2gTpMessage() {
		return v2gTpMessage;
	}

	public void setV2gTpMessage(V2GTPMessage v2gTpMessage) {
		this.v2gTpMessage = v2gTpMessage;
	}
	
	
	public void setMessageHandler(MessageHandler messageHandler) {
		this.messageHandler = messageHandler;
	}


	public V2GMessage getV2gMessage() {
		return v2gMessage;
	}


	public void setV2gMessage(V2GMessage v2gMessage) {
		this.v2gMessage = v2gMessage;
	}


	public boolean isSecureCommunication() {
		return secureCommunication;
	}


	public void setSecureCommunication(boolean secureCommunication) {
		this.secureCommunication = secureCommunication;
	}
}
