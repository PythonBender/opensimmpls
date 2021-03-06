/* 
 * Copyright 2015 (C) Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package simMPLS.scenario;

import java.awt.Point;
import java.util.Iterator;
import simMPLS.protocols.TGPSRPPDU;
import simMPLS.protocols.TTLDPPDU;
import simMPLS.protocols.TGPSRPPayload;
import simMPLS.protocols.TAbstractPDU;
import simMPLS.protocols.TMPLSLabel;
import simMPLS.protocols.TMPLSPDU;
import simMPLS.protocols.TTLDPPayload;
import simMPLS.protocols.TIPv4PDU;
import simMPLS.hardware.timer.TTimerEvent;
import simMPLS.hardware.timer.ITimerEventListener;
import simMPLS.hardware.ports.TActivePortSet;
import simMPLS.hardware.ports.TActivePort;
import simMPLS.hardware.ports.TFIFOPort;
import simMPLS.hardware.dmgp.TDMGP;
import simMPLS.hardware.tldp.TSwitchingMatrix;
import simMPLS.hardware.tldp.TSwitchingMatrixEntry;
import simMPLS.hardware.dmgp.TGPSRPRequestsMatrix;
import simMPLS.hardware.dmgp.TGPSRPRequestEntry;
import simMPLS.hardware.ports.TPort;
import simMPLS.hardware.ports.TPortSet;
import simMPLS.utils.EIDGeneratorOverflow;
import simMPLS.utils.TIDGenerator;
import simMPLS.utils.TLongIDGenerator;

/**
 * This class implements an active Label Edge Router (LER) node that will allow
 * network traffic to entry/exit to/from the MPLS domain.
 *
 * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
 * @version 2.0
 */
public class TActiveLERNode extends TNode implements ITimerEventListener, Runnable {

    /**
     * This method is the constructor of the class. It is create a new instance
     * of TActiveLERNode.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param identifier the identifier of this active LER node that allow
     * referencing switchingMatrixIterator in the topology.
     * @param ipAddress The IPv4 address assigned to this active LER.
     * @param longIDGenerator The idntifier generator that the active LER will
     * use to identify unambiguosly each event switchingMatrixIterator
     * generates.
     * @param topology A reference to the topology this active LER belongs to.
     * @since 2.0
     */
    public TActiveLERNode(int identifier, String ipAddress, TLongIDGenerator longIDGenerator, TTopology topology) {
        super(identifier, ipAddress, longIDGenerator, topology);
        // FIX: This is an overridable method call in constructor that should be 
        // avoided.
        this.setPorts(TNode.NUM_LERA_PORTS);
        this.switchingMatrix = new TSwitchingMatrix();
        this.gIdent = new TLongIDGenerator();
        this.gIdentLDP = new TIDGenerator();
        //FIX: replace with class constants.
        this.switchingPowerInMbps = 512;
        this.dmgp = new TDMGP();
        this.gpsrpRequests = new TGPSRPRequestsMatrix();
        this.stats = new TLERAStats();
    }

    /**
     * This method returns the size of the local DMGP (see the "Guarantee Of
     * Service Support Over MPLS Using Active Techniques" proposal) in KBytes.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @return the current size of DMGP in KBytes.
     * @since 2.0
     */
    public int getDMGPSizeInKB() {
        return this.dmgp.getDMGPSizeInKB();
    }

    /**
     * This method sets the size of the local DMGP (see the "Guarantee Of
     * Service Support Over MPLS Using Active Techniques" proposal) in KBytes.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param sizeInKB the desired size of DMGP in KBytes.
     * @since 2.0
     */
    public void setDMGPSizeInKB(int sizeInKB) {
        this.dmgp.setDMGPSizeInKB(sizeInKB);
    }

    /**
     * This method computes and returns the number of nanoseconds that are
     * needed to switch a single bit. This is something that depends on the
     * switching power of this active LER.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @return the number of nanoseconds that are needed to switch a single bit.
     * @since 2.0
     */
    public double getNsPerBit() {
        // FIX: replace al harcoded values for class constants
        long bitsPerSecondRate = (long) (this.switchingPowerInMbps * 1048576L);
        double nsPerBit = (double) ((double) 1000000000.0 / (long) bitsPerSecondRate);
        return nsPerBit;
    }

    /**
     * This method computes and returns the number of nanoseconds that are
     * needed to switch the specified number of octects. This is something that
     * depends on the switching power of this active LER.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param octects the number of octects that wants to be switched.
     * @return the number of nanoseconds that are needed to switch the specified
     * number of octects.
     * @since 2.0
     */
    public double getNsRequiredForAllOctets(int octects) {
        // FIX: replace al harcoded values for class constants
        double nsPerBit = this.getNsPerBit();
        long numberOfBits = (long) ((long) octects * (long) 8);
        return (double) ((double) nsPerBit * (long) numberOfBits);
    }

    /**
     * This method gets the number of bits that this LERA can route with the
     * available number of nanoseconds it has. The more switching power the LERA
     * has, the more bits it can switch with the same number of nanoseconds.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @return the current size of DMGP in KBytes.
     * @since 2.0
     */
    public int getMaxSwitchableBitsWithCurrentNs() {
        double nsPerBit = getNsPerBit();
        double maxNumberOfBits = (double) ((double) this.availableNs / (double) nsPerBit);
        return (int) maxNumberOfBits;
    }

    /**
     * This method gets the number of octects that this LERA can route with the
     * available number of nanoseconds it has. The more switching power the LERA
     * has, the more octects it can switch with the same number of nanoseconds.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @return the current size of DMGP in KBytes.
     * @since 2.0
     */
    public int getMaxSwitchableOctectsWithCurrentNs() {
        // FIX: replace al harcoded values for class constants
        double maxNumberOfOctects = ((double) getMaxSwitchableBitsWithCurrentNs() / (double) 8.0);
        return (int) maxNumberOfOctects;
    }

    /**
     * This method gets the switching power of this LERA, in Mbps.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @return the current size of DMGP in KBytes.
     * @since 2.0
     */
    public int getSwitchingPowerInMbps() {
        return this.switchingPowerInMbps;
    }

    /**
     * This method sets the switching power of this LERA, in Mbps.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param switchingPowerInMbps the switching power of this LERA, in Mbps.
     * @since 2.0
     */
    public void setSwitchingPowerInMbps(int switchingPowerInMbps) {
        this.switchingPowerInMbps = switchingPowerInMbps;
    }

    /**
     * This method gets the size of this LERA's buffer, in MBytes.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @return the size of this LERA's buffer, in MBytes.
     * @since 2.0
     */
    public int getBufferSizeInMBytes() {
        return this.getPorts().getBufferSizeInMB();
    }

    /**
     * This method sets the size of this LERA's buffer, in MBytes.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param bufferSizeInMBytes the size of this LERA's buffer, in MBytes.
     * @since 2.0
     */
    public void setBufferSizeInMBytes(int bufferSizeInMBytes) {
        this.getPorts().setBufferSizeInMB(bufferSizeInMBytes);
    }

    /**
     * This method restart the attributes of the class as in the creation of the
     * instance.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @since 2.0
     */
    @Override
    public void reset() {
        this.ports.reset();
        this.switchingMatrix.reset();
        this.gIdent.reset();
        this.gIdentLDP.reset();
        this.stats.reset();
        this.stats.activateStats(this.isGeneratingStats());
        this.dmgp.reset();
        this.gpsrpRequests.reset();
        this.resetStepsWithoutEmittingToZero();
    }

    /**
     * This method gets the type of this node. In this case, it will return the
     * constant TNode.LERA.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @return the constant TNode.LERA.
     * @since 2.0
     */
    @Override
    public int getNodeType() {
        return TNode.LERA;
    }

    /**
     * This method receive a timer event from the simulation's global timer. It
     * does some initial tasks and then wake up the LERA to start doing its
     * work.
     *
     * @param timerEvent a timer event that is used to synchronize all nodes and
     * that inclues a number of nanoseconds to be used by the LERA.
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @since 2.0
     */
    @Override
    public void receiveTimerEvent(TTimerEvent timerEvent) {
        this.setStepDuration(timerEvent.getStepDuration());
        this.setTimeInstant(timerEvent.getUpperLimit());
        if (this.getPorts().isThereAnyPacketToRoute()) {
            this.availableNs += timerEvent.getStepDuration();
        } else {
            this.resetStepsWithoutEmittingToZero();
            this.availableNs = timerEvent.getStepDuration();
        }
        this.startOperation();
    }

    /**
     * This method starts all tasks that has to be executed during a timer tick.
     * The number of nanoseconds of this tick is the time this LERA will work.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @since 2.0
     */
    @Override
    public void run() {
        // Acciones a llevar a cabo durante el tic.
        try {
            this.generateSimulationEvent(new TSENodeCongested(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), this.getPorts().getCongestionLevel()));
        } catch (Exception e) {
            // FIX: this is not a good practice. Avoid.
            e.printStackTrace();
        }
        this.checkConnectivityStatus();
        this.decreaseCounters();
        this.routePackets();
        this.stats.consolidateData(this.getAvailableTime());
    }

    /**
     * This method check wether the connectivity to the neighbors nodes exist.
     * Let's say, this check whether a link of this node is down. If so, this
     * method generates the corresponding event to notify the situation.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @since 2.0
     */
    public void checkConnectivityStatus() {
        boolean removeSwitchingMatrixEntry = false;
        TSwitchingMatrixEntry switchingMatrixEntry = null;
        // FIX: Avoid using harcoded values
        int portIDAux = 0;
        TPort outgoingPort = null;
        TPort outgoingBackupPort = null;
        TPort incomingPort = null;
        TLink linkAux1 = null;
        TLink linkAux2 = null;
        this.switchingMatrix.getMonitor().lock();
        Iterator switchingMatrixIterator = this.switchingMatrix.getEntriesIterator();
        while (switchingMatrixIterator.hasNext()) {
            switchingMatrixEntry = (TSwitchingMatrixEntry) switchingMatrixIterator.next();
            if (switchingMatrixEntry != null) {
                portIDAux = switchingMatrixEntry.getBackupOutgoingPortID();
                if ((portIDAux >= 0) && (portIDAux < this.ports.getNumberOfPorts())) {
                    outgoingBackupPort = this.ports.getPort(portIDAux);
                    if (outgoingBackupPort != null) {
                        linkAux1 = outgoingBackupPort.getLink();
                        if (linkAux1 != null) {
                            if ((linkAux1.isBroken()) && (switchingMatrixEntry.getOutgoingLabel() != TSwitchingMatrixEntry.REMOVING_LABEL)) {
                                if (switchingMatrixEntry.backupLSPHasBeenEstablished() || switchingMatrixEntry.backupLSPShouldBeRemoved()) {
                                    switchingMatrixEntry.setBackupOutgoingLabel(TSwitchingMatrixEntry.UNDEFINED);
                                    switchingMatrixEntry.setBackupOutgoingPortID(TSwitchingMatrixEntry.UNDEFINED);
                                }
                            }
                        }
                    }
                }
                portIDAux = switchingMatrixEntry.getOutgoingPortID();
                if ((portIDAux >= 0) && (portIDAux < this.ports.getNumberOfPorts())) {
                    outgoingPort = this.ports.getPort(portIDAux);
                    if (outgoingPort != null) {
                        linkAux1 = outgoingPort.getLink();
                        if (linkAux1 != null) {
                            if ((linkAux1.isBroken()) && (switchingMatrixEntry.getOutgoingLabel() != TSwitchingMatrixEntry.REMOVING_LABEL)) {
                                incomingPort = this.ports.getPort(switchingMatrixEntry.getIncomingPortID());
                                linkAux1 = incomingPort.getLink();
                                if (linkAux1.getLinkType() == TLink.INTERNAL) {
                                    this.sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getIncomingPortID());
                                } else {
                                    removeSwitchingMatrixEntry = true;
                                }
                            }
                        }
                    }
                }
                portIDAux = switchingMatrixEntry.getIncomingPortID();
                if ((portIDAux >= 0) && (portIDAux < this.ports.getNumberOfPorts())) {
                    incomingPort = this.ports.getPort(portIDAux);
                    if (incomingPort != null) {
                        linkAux1 = incomingPort.getLink();
                        if (linkAux1 != null) {
                            if ((linkAux1.isBroken()) && (switchingMatrixEntry.getOutgoingLabel() != TSwitchingMatrixEntry.REMOVING_LABEL)) {
                                outgoingPort = this.ports.getPort(switchingMatrixEntry.getOutgoingPortID());
                                linkAux1 = outgoingPort.getLink();
                                if (linkAux1.getLinkType() == TLink.INTERNAL) {
                                    this.sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getOutgoingPortID());
                                } else {
                                    removeSwitchingMatrixEntry = false;
                                }
                            }
                        }
                    }
                }
                if ((switchingMatrixEntry.getIncomingPortID() >= 0) && ((switchingMatrixEntry.getOutgoingPortID() >= 0))) {
                    linkAux1 = ports.getPort(switchingMatrixEntry.getIncomingPortID()).getLink();
                    linkAux2 = ports.getPort(switchingMatrixEntry.getOutgoingPortID()).getLink();
                    if (linkAux1.isBroken() && linkAux2.isBroken()) {
                        removeSwitchingMatrixEntry = true;
                    }
                    if (linkAux1.isBroken() && (linkAux2.getLinkType() == TLink.EXTERNAL)) {
                        removeSwitchingMatrixEntry = true;
                    }
                    if ((linkAux1.getLinkType() == TLink.EXTERNAL) && linkAux2.isBroken()) {
                        removeSwitchingMatrixEntry = true;
                    }
                } else {
                    removeSwitchingMatrixEntry = true;
                }
                if (removeSwitchingMatrixEntry) {
                    switchingMatrixIterator.remove();
                }
            } else {
                switchingMatrixIterator.remove();
            }
        }
        this.switchingMatrix.getMonitor().unLock();
        this.gpsrpRequests.decreaseTimeout(this.getTickDuration());
        this.gpsrpRequests.updateEntries();
        int numberOfPorts = this.ports.getNumberOfPorts();
        int i = 0;
        TActivePort currentPort = null;
        TLink linkOfPort = null;
        for (i = 0; i < numberOfPorts; i++) {
            currentPort = (TActivePort) this.ports.getPort(i);
            if (currentPort != null) {
                linkOfPort = currentPort.getLink();
                if (linkOfPort != null) {
                    if (linkOfPort.isBroken()) {
                        this.gpsrpRequests.removeEntriesMatchingOutgoingPort(i);
                    }
                }
            }
        }
        this.gpsrpRequests.getMonitor().lock();
        Iterator gpsrpRequestsIterator = this.gpsrpRequests.getEntriesIterator();
        int flowID = 0;
        int packetID = 0;
        String targetIPv4Address = null;
        int outgoingPortAux = 0;
        TGPSRPRequestEntry gpsrpRequestEntry = null;
        while (gpsrpRequestsIterator.hasNext()) {
            gpsrpRequestEntry = (TGPSRPRequestEntry) gpsrpRequestsIterator.next();
            if (gpsrpRequestEntry.isRetryable()) {
                flowID = gpsrpRequestEntry.getFlowID();
                packetID = gpsrpRequestEntry.getPacketID();
                targetIPv4Address = gpsrpRequestEntry.getCrossedNodeIPv4();
                outgoingPortAux = gpsrpRequestEntry.getOutgoingPort();
                this.requestGPSRP(flowID, packetID, targetIPv4Address, outgoingPortAux);
            }
            gpsrpRequestEntry.resetTimeout();
        }
        this.gpsrpRequests.getMonitor().unLock();
    }

    /**
     * This method read read the port to which it is up following a Round Robin
     * algorithm. It does that until it consumes all the available nanoseconds
     * for the current tick/step. If it is able to switch or route a given
     * incoming packet, it does it. If it is a martian packets, the paceket is
     * discarded.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @since 2.0
     */
    public void routePackets() {
        boolean atLeastOnePacketRouted = false;
        int readPort = 0;
        TAbstractPDU packet = null;
        int switchableOctectsWithCurrentNs = this.getMaxSwitchableOctectsWithCurrentNs();
        while (this.getPorts().canSwitchPacket(switchableOctectsWithCurrentNs)) {
            atLeastOnePacketRouted = true;
            packet = this.ports.getNextPacket();
            readPort = this.ports.getReadPort();
            if (packet != null) {
                if (packet.getType() == TAbstractPDU.IPV4) {
                    handleIPv4Packet((TIPv4PDU) packet, readPort);
                } else if (packet.getType() == TAbstractPDU.TLDP) {
                    handleTLDPPacket((TTLDPPDU) packet, readPort);
                } else if (packet.getType() == TAbstractPDU.MPLS) {
                    handleMPLSPacket((TMPLSPDU) packet, readPort);
                } else if (packet.getType() == TAbstractPDU.GPSRP) {
                    handleGPSRPPacket((TGPSRPPDU) packet, readPort);
                } else {
                    this.availableNs += getNsRequiredForAllOctets(packet.getSize());
                    discardPacket(packet);
                }
                this.availableNs -= getNsRequiredForAllOctets(packet.getSize());
                switchableOctectsWithCurrentNs = this.getMaxSwitchableOctectsWithCurrentNs();
            }
        }
        if (atLeastOnePacketRouted) {
            this.resetStepsWithoutEmittingToZero();
        } else {
            this.increaseStepsWithoutEmitting();
        }
    }

    /**
     * This method switchs an incoming GPDRP packet.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param packet GPSRP packet to switch.
     * @param incomingPortID Port of this node where the packet has arrived.
     * @since 2.0
     */
    public void handleGPSRPPacket(TGPSRPPDU packet, int incomingPortID) {
        if (packet != null) {
            int messageType = packet.getGPSRPPayload().getGPSRPMessageType();
            // FIX: flowID and packetID seems not to be used. If not necessary,
            // remove from the code.
            int flowID = packet.getGPSRPPayload().getFlowID();
            int packetID = packet.getGPSRPPayload().getPacketID();
            String targetIPv4Address = packet.getIPv4Header().getTailEndIPAddress();
            TFIFOPort outgoingPort = null;
            if (targetIPv4Address.equals(this.getIPAddress())) {
                if (messageType == TGPSRPPayload.RETRANSMISSION_REQUEST) {
                    this.handleGPSRPRetransmissionRequest(packet, incomingPortID);
                } else if (messageType == TGPSRPPayload.RETRANSMISION_NOT_POSSIBLE) {
                    this.handleGPSRPRetransmissionNotPossible(packet, incomingPortID);
                } else if (messageType == TGPSRPPayload.RETRANSMISION_OK) {
                    this.handleGPSRPRetransmissionOk(packet, incomingPortID);
                }
            } else {
                String nextHopIPv4Address = this.topology.getNextHopRABANIPv4Address(this.getIPAddress(), targetIPv4Address);
                outgoingPort = (TFIFOPort) this.ports.getLocalPortConnectedToANodeWithIPAddress(nextHopIPv4Address);
                if (outgoingPort != null) {
                    outgoingPort.putPacketOnLink(packet, outgoingPort.getLink().getTargetNodeIDOfTrafficSentBy(this));
                    try {
                        this.generateSimulationEvent(new TSEPacketRouted(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.GPSRP));
                    } catch (Exception e) {
                        // FIX: This is not a good practice. Avoid.
                        e.printStackTrace();
                    }
                } else {
                    this.discardPacket(packet);
                }
            }
        }
    }

    /**
     * This method handles a GPSRP request of retransmission.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param packet GPSRP packet to switch.
     * @param incomingPortID Port of this node where the packet has arrived.
     * @since 2.0
     */
    public void handleGPSRPRetransmissionRequest(TGPSRPPDU packet, int incomingPortID) {
        int flowID = packet.getGPSRPPayload().getFlowID();
        int packetID = packet.getGPSRPPayload().getPacketID();
        TMPLSPDU wantedPacket = (TMPLSPDU) this.dmgp.getPacket(flowID, packetID);
        if (wantedPacket != null) {
            this.acceptGPSRP(packet, incomingPortID);
            TActivePort outgoingPort = (TActivePort) this.ports.getPort(incomingPortID);
            if (outgoingPort != null) {
                outgoingPort.putPacketOnLink(wantedPacket, outgoingPort.getLink().getTargetNodeIDOfTrafficSentBy(this));
                try {
                    this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), wantedPacket.getSubtype()));
                } catch (Exception e) {
                    // FIX: this is not a good practice. Avoid.
                    e.printStackTrace();
                }
            }
        } else {
            this.rejectGPSRP(packet, incomingPortID);
        }
    }

    /**
     * This method handles a GPSRP denial of retransmission.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param packet GPSRP packet to switch.
     * @param incomingPortID Port of this node where the packet has arrived.
     * @since 2.0
     */
    public void handleGPSRPRetransmissionNotPossible(TGPSRPPDU packet, int incomingPortID) {
        int flowID = packet.getGPSRPPayload().getFlowID();
        int packetID = packet.getGPSRPPayload().getPacketID();
        TGPSRPRequestEntry gpsrpRequestEntry = this.gpsrpRequests.getEntry(flowID, packetID);
        if (gpsrpRequestEntry != null) {
            gpsrpRequestEntry.forceTimeoutReset();
            int outgoingPortAux = gpsrpRequestEntry.getOutgoingPort();
            if (!gpsrpRequestEntry.isPurgeable()) {
                String targetIPv4Address = gpsrpRequestEntry.getCrossedNodeIPv4();
                if (targetIPv4Address != null) {
                    requestGPSRP(flowID, packetID, targetIPv4Address, outgoingPortAux);
                } else {
                    this.gpsrpRequests.removeEntry(flowID, packetID);
                }
            } else {
                this.gpsrpRequests.removeEntry(flowID, packetID);
            }
        }
    }

    /**
     * This method handles a GPSRP acceptance of retransmission.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param packet GPSRP packet to switch.
     * @param incomingPortID Port of this node where the packet has arrived.
     * @since 2.0
     */
    public void handleGPSRPRetransmissionOk(TGPSRPPDU packet, int incomingPortID) {
        int flowID = packet.getGPSRPPayload().getFlowID();
        int packetID = packet.getGPSRPPayload().getPacketID();
        this.gpsrpRequests.removeEntry(flowID, packetID);
    }

    /**
     * This method start the GPSRP protocol to request the retransmission of a
     * lost packet part of wich has been recovered before discarding it.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param packet incomplete MPLS packet that want to be recovered locally.
     * @param outgoingPortID Port of this node through wich the GPSRP
     * retransmission request is going to be sent.
     * @since 2.0
     */
    @Override
    public void runGoSPDUStoreAndRetransmitProtocol(TMPLSPDU packet, int outgoingPortID) {
        TGPSRPRequestEntry gpsrpRequestEntry = null;
        gpsrpRequestEntry = this.gpsrpRequests.addEntry(packet, outgoingPortID);
        if (gpsrpRequestEntry != null) {
            TActivePort outgoingPort = (TActivePort) this.ports.getPort(outgoingPortID);
            TGPSRPPDU gpsrpPacket = null;
            String targetIPv4Address = gpsrpRequestEntry.getCrossedNodeIPv4();
            if (targetIPv4Address != null) {
                try {
                    gpsrpPacket = new TGPSRPPDU(this.gIdent.getNextID(), this.getIPAddress(), targetIPv4Address);
                } catch (Exception e) {
                    //FIX: This is not a good practice. Avoid.
                    e.printStackTrace();
                }
                // FIX: gpsrPacket could be null if the previous try generates 
                // an exception. 
                gpsrpPacket.getGPSRPPayload().setFlowID(gpsrpRequestEntry.getFlowID());
                gpsrpPacket.getGPSRPPayload().setPacketID(gpsrpRequestEntry.getPacketID());
                gpsrpPacket.getGPSRPPayload().setGPSRPMessageType(TGPSRPPayload.RETRANSMISSION_REQUEST);
                outgoingPort.putPacketOnLink(gpsrpPacket, outgoingPort.getLink().getTargetNodeIDOfTrafficSentBy(this));
                try {
                    this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.GPSRP, gpsrpPacket.getSize()));
                    this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.GPSRP));
                } catch (Exception e) {
                    //FIX: This is not a good practice. Avoid.
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * This method start the GPSRP protocol to request the retransmission of a
     * lost packet part of whose data has been recovered before discarding it.
     *
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @param flowID flow ID for wich the retransmision is requested.
     * @param outgoingPortID Port of this node through wich the GPSRP
     * retransmission request is going to be sent.
     * @param packetID packet, of the specified flow, for wich the
     * retransmission is requested.
     * @param targetIPv4Address IP address of the node to wich the
     * retransmission request is sent. Hopefuly, the one that could retransmit
     * the lost packet.
     * @since 2.0
     */
    public void requestGPSRP(int flowID, int packetID, String targetIPv4Address, int outgoingPortID) {
        TActivePort outgoingPort = (TActivePort) this.ports.getPort(outgoingPortID);
        TGPSRPPDU gpsrpPacket = null;
        if (targetIPv4Address != null) {
            try {
                gpsrpPacket = new TGPSRPPDU(this.gIdent.getNextID(), this.getIPAddress(), targetIPv4Address);
            } catch (Exception e) {
                //FIX: This is not a good practice. Avoid.
                e.printStackTrace();
            }
            // FIX: gpsrPacket could be null if the previous try generates an 
            // exception. 
            gpsrpPacket.getGPSRPPayload().setFlowID(flowID);
            gpsrpPacket.getGPSRPPayload().setPacketID(packetID);
            gpsrpPacket.getGPSRPPayload().setGPSRPMessageType(TGPSRPPayload.RETRANSMISSION_REQUEST);
            outgoingPort.putPacketOnLink(gpsrpPacket, outgoingPort.getLink().getTargetNodeIDOfTrafficSentBy(this));
            try {
                this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.GPSRP, gpsrpPacket.getSize()));
                this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.GPSRP));
            } catch (Exception e) {
                //FIX: This is not a good practice. Avoid.
                e.printStackTrace();
            }
        }
    }

    /**
     * This method deny a packet retransmission.
     *
     * @param packet GPSRP packet requesting a packet retransmission.
     * @param outgoingPortID Outgoing port to be used to send the denial.
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @since 2.0
     */
    public void rejectGPSRP(TGPSRPPDU packet, int outgoingPortID) {
        TActivePort outgoingPort = (TActivePort) this.ports.getPort(outgoingPortID);
        if (outgoingPort != null) {
            TGPSRPPDU gpsrpPacket = null;
            try {
                gpsrpPacket = new TGPSRPPDU(this.gIdent.getNextID(), this.getIPAddress(), packet.getIPv4Header().getOriginIPAddress());
            } catch (Exception e) {
                //FIX: This is not a good practice. Avoid.
                e.printStackTrace();
            }
            // FIX: gpsrPacket could be null if the previous try generates an 
            // exception. 
            gpsrpPacket.getGPSRPPayload().setFlowID(packet.getGPSRPPayload().getFlowID());
            gpsrpPacket.getGPSRPPayload().setPacketID(packet.getGPSRPPayload().getPacketID());
            gpsrpPacket.getGPSRPPayload().setGPSRPMessageType(TGPSRPPayload.RETRANSMISION_NOT_POSSIBLE);
            outgoingPort.putPacketOnLink(gpsrpPacket, outgoingPort.getLink().getTargetNodeIDOfTrafficSentBy(this));
            try {
                this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.GPSRP, gpsrpPacket.getSize()));
                this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.GPSRP));
            } catch (Exception e) {
                //FIX: This is not a good practice. Avoid.
                e.printStackTrace();
            }
        } else {
            discardPacket(packet);
        }
    }

    /**
     * This method accepts/confirms a packet retransmission.
     *
     * @param packet GPSRP packet requesting a packet retransmission.
     * @param outgoingPortID Outgoing port to be used to send the acceptance.
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     * @since 2.0
     */
    public void acceptGPSRP(TGPSRPPDU packet, int outgoingPortID) {
        TActivePort outgoingPort = (TActivePort) this.ports.getPort(outgoingPortID);
        if (outgoingPort != null) {
            TGPSRPPDU gpsrpPacket = null;
            try {
                gpsrpPacket = new TGPSRPPDU(this.gIdent.getNextID(), this.getIPAddress(), packet.getIPv4Header().getOriginIPAddress());
            } catch (Exception e) {
                //FIX: This is not a good practice. Avoid.
                e.printStackTrace();
            }
            // FIX: gpsrPacket could be null if the previous try generates an 
            // exception. 
            gpsrpPacket.getGPSRPPayload().setFlowID(packet.getGPSRPPayload().getFlowID());
            gpsrpPacket.getGPSRPPayload().setPacketID(packet.getGPSRPPayload().getPacketID());
            gpsrpPacket.getGPSRPPayload().setGPSRPMessageType(TGPSRPPayload.RETRANSMISION_OK);
            outgoingPort.putPacketOnLink(gpsrpPacket, outgoingPort.getLink().getTargetNodeIDOfTrafficSentBy(this));
            try {
                this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.GPSRP, gpsrpPacket.getSize()));
                this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.GPSRP));
            } catch (Exception e) {
                //FIX: This is not a good practice. Avoid.
                e.printStackTrace();
            }
        } else {
            discardPacket(packet);
        }
    }

    /**
     * This method cheks whether there is an entry for the incoming IPv4 packet
     * in the routing table. If not, this method classifies the packet and, if
     * needed, it re-enqueues the packet and requests a label to be able to
     * relay the packet. Once an entry for this packet is in the routing table,
     * it sends the packet into the MPLS domain or outward, as appropriate.
     *
     * @param packet Incoming IPv4 packet.
     * @param incomingPortID Port of this node from wich the IPv4 packet has
     * arrived.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void handleIPv4Packet(TIPv4PDU packet, int incomingPortID) {
        int fec = classifyPacket(packet);
        String targetIPv4Address = packet.getIPv4Header().getTailEndIPAddress();
        TSwitchingMatrixEntry switchingMatrixEntry = null;
        boolean requireBackupLSP = false;
        if ((packet.getIPv4Header().getOptionsField().getRequestedGoSLevel() == TAbstractPDU.EXP_LEVEL0_WITH_BACKUP_LSP)
                || (packet.getIPv4Header().getOptionsField().getRequestedGoSLevel() == TAbstractPDU.EXP_LEVEL1_WITH_BACKUP_LSP)
                || (packet.getIPv4Header().getOptionsField().getRequestedGoSLevel() == TAbstractPDU.EXP_LEVEL2_WITH_BACKUP_LSP)
                || (packet.getIPv4Header().getOptionsField().getRequestedGoSLevel() == TAbstractPDU.EXP_LEVEL3_WITH_BACKUP_LSP)) {
            requireBackupLSP = true;
        }
        switchingMatrixEntry = this.switchingMatrix.getEntry(incomingPortID, fec, TSwitchingMatrixEntry.FEC_ENTRY);
        if (switchingMatrixEntry == null) {
            switchingMatrixEntry = createInitialEntryInFECMatrix(packet, incomingPortID);
            if (switchingMatrixEntry != null) {
                if (!isExitActiveLER(targetIPv4Address)) {
                    switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.LABEL_REQUESTED);
                    requestTLDP(switchingMatrixEntry);
                }
                this.ports.getPort(incomingPortID).reEnqueuePacket(packet);
            }
        }
        if (switchingMatrixEntry != null) {
            int currentLabel = switchingMatrixEntry.getOutgoingLabel();
            if (currentLabel == TSwitchingMatrixEntry.UNDEFINED) {
                switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.LABEL_REQUESTED);
                requestTLDP(switchingMatrixEntry);
                this.ports.getPort(switchingMatrixEntry.getIncomingPortID()).reEnqueuePacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                this.ports.getPort(switchingMatrixEntry.getIncomingPortID()).reEnqueuePacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.REMOVING_LABEL) {
                discardPacket(packet);
                // FIX: Avoid using hardcoded values. Use class constants instead.
            } else if ((currentLabel > 15) || (currentLabel == TSwitchingMatrixEntry.LABEL_ASSIGNED)) {
                int operation = switchingMatrixEntry.getLabelStackOperation();
                if (operation == TSwitchingMatrixEntry.UNDEFINED) {
                    discardPacket(packet);
                } else if (operation == TSwitchingMatrixEntry.PUSH_LABEL) {
                    if (requireBackupLSP) {
                        requestTLDPForBackupLSP(switchingMatrixEntry);
                    }
                    TPort outgoingPort = this.ports.getPort(switchingMatrixEntry.getOutgoingPortID());
                    TMPLSPDU mplsPacket = this.createMPLSPacket(packet, switchingMatrixEntry);
                    if (packet.getSubtype() == TAbstractPDU.IPV4_GOS) {
                        int expFieldAux = packet.getIPv4Header().getOptionsField().getRequestedGoSLevel();
                        TMPLSLabel mplsLabelAux = new TMPLSLabel();
                        // FIX: Avoid using hardcoded values. Use class 
                        // constants instead.
                        mplsLabelAux.setBoS(false);
                        mplsLabelAux.setEXP(expFieldAux);
                        mplsLabelAux.setLabel(1);
                        mplsLabelAux.setTTL(packet.getIPv4Header().getTTL());
                        mplsPacket.getLabelStack().pushTop(mplsLabelAux);
                        mplsPacket.setSubtype(TAbstractPDU.MPLS_GOS);
                        mplsPacket.getIPv4Header().getOptionsField().setCrossedActiveNode(this.getIPAddress());
                        this.dmgp.addPacket(mplsPacket);
                    }
                    outgoingPort.putPacketOnLink(mplsPacket, outgoingPort.getLink().getTargetNodeIDOfTrafficSentBy(this));
                    try {
                        this.generateSimulationEvent(new TSEPacketRouted(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), packet.getSubtype()));
                    } catch (Exception e) {
                        // FIX: Avoid this. This is not a good practice.
                        e.printStackTrace();
                    }
                } else if (operation == TSwitchingMatrixEntry.POP_LABEL) {
                    discardPacket(packet);
                } else if (operation == TSwitchingMatrixEntry.SWAP_LABEL) {
                    discardPacket(packet);
                } else if (operation == TSwitchingMatrixEntry.NOOP) {
                    TPort outgoingPort = this.ports.getPort(switchingMatrixEntry.getOutgoingPortID());
                    outgoingPort.putPacketOnLink(packet, outgoingPort.getLink().getTargetNodeIDOfTrafficSentBy(this));
                    try {
                        this.generateSimulationEvent(new TSEPacketRouted(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), packet.getSubtype()));
                    } catch (Exception e) {
                        // FIX: Avoid this. This is not a good practice.
                        e.printStackTrace();
                    }
                }
            } else {
                discardPacket(packet);
            }
        } else {
            discardPacket(packet);
        }
    }

    /**
     * This method is called when a TLDP packet is received. It performs the
     * required action in the routing matrix and propagates the changes to the
     * correspondant adjacent node.
     *
     * @param packet TLDP packet received.
     * @param incomingPortID Port of this node from wich the TLDP packet has
     * arrived.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void handleTLDPPacket(TTLDPPDU packet, int incomingPortID) {
        if (packet.getTLDPPayload().getTLDPMessageType() == TTLDPPayload.LABEL_REQUEST) {
            this.handleTLDPRequest(packet, incomingPortID);
        } else if (packet.getTLDPPayload().getTLDPMessageType() == TTLDPPayload.LABEL_REQUEST_OK) {
            this.handleTLDPRequestOk(packet, incomingPortID);
        } else if (packet.getTLDPPayload().getTLDPMessageType() == TTLDPPayload.LABEL_REQUEST_DENIED) {
            this.handleTLDPRefuseRequest(packet, incomingPortID);
        } else if (packet.getTLDPPayload().getTLDPMessageType() == TTLDPPayload.LABEL_REMOVAL_REQUEST) {
            this.handleTLDPWithdrawal(packet, incomingPortID);
        } else if (packet.getTLDPPayload().getTLDPMessageType() == TTLDPPayload.LABEL_REVOMAL_REQUEST_OK) {
            this.handleTLDPWithdrawalOk(packet, incomingPortID);
        }
    }

    /**
     * This method checks whether there is an entry for the incoming MPLS packet
     * in the routing table. If not, this method classifies the packet and, if
     * needed, it re-enqueues the packet and requests a label to be able to
     * relay the packet. Once an entry for this packet is in the routing table,
     * it sends the packet into the MPLS domain or outward, as appropriate.
     *
     * @param packet MPLS packet receivede.
     * @param incomingPortID Port of this node from wich the MPLS packet has
     * arrived.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void handleMPLSPacket(TMPLSPDU packet, int incomingPortID) {
        TMPLSLabel mplsLabel = null;
        TSwitchingMatrixEntry switchingMatrixEntry = null;
        boolean isLabeled = false;
        boolean requireBackupLSP = false;
        // FIX: Do not use harcoded values. Use class constants instead.
        if (packet.getLabelStack().getTop().getLabel() == 1) {
            mplsLabel = packet.getLabelStack().getTop();
            packet.getLabelStack().popTop();
            isLabeled = true;
            if ((mplsLabel.getEXP() == TAbstractPDU.EXP_LEVEL0_WITH_BACKUP_LSP)
                    || (mplsLabel.getEXP() == TAbstractPDU.EXP_LEVEL1_WITH_BACKUP_LSP)
                    || (mplsLabel.getEXP() == TAbstractPDU.EXP_LEVEL2_WITH_BACKUP_LSP)
                    || (mplsLabel.getEXP() == TAbstractPDU.EXP_LEVEL3_WITH_BACKUP_LSP)) {
                requireBackupLSP = true;
            }
        }
        int labelValue = packet.getLabelStack().getTop().getLabel();
        String targetIPv4Address = packet.getIPv4Header().getTailEndIPAddress();
        switchingMatrixEntry = this.switchingMatrix.getEntry(incomingPortID, labelValue, TSwitchingMatrixEntry.LABEL_ENTRY);
        if (switchingMatrixEntry == null) {
            switchingMatrixEntry = createInitialEntryInILMMatrix(packet, incomingPortID);
            if (switchingMatrixEntry != null) {
                if (!isExitActiveLER(targetIPv4Address)) {
                    switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.LABEL_REQUESTED);
                    requestTLDP(switchingMatrixEntry);
                }
            }
        }
        if (switchingMatrixEntry != null) {
            int currentLabel = switchingMatrixEntry.getOutgoingLabel();
            if (currentLabel == TSwitchingMatrixEntry.UNDEFINED) {
                switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.LABEL_REQUESTED);
                requestTLDP(switchingMatrixEntry);
                if (isLabeled) {
                    packet.getLabelStack().pushTop(mplsLabel);
                }
                this.ports.getPort(switchingMatrixEntry.getIncomingPortID()).reEnqueuePacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                if (isLabeled) {
                    packet.getLabelStack().pushTop(mplsLabel);
                }
                this.ports.getPort(switchingMatrixEntry.getIncomingPortID()).reEnqueuePacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                if (isLabeled) {
                    packet.getLabelStack().pushTop(mplsLabel);
                }
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.REMOVING_LABEL) {
                if (isLabeled) {
                    packet.getLabelStack().pushTop(mplsLabel);
                }
                discardPacket(packet);
                // FIX: Do not use hardcoded values. Use class constants instead.
            } else if ((currentLabel > 15) || (currentLabel == TSwitchingMatrixEntry.LABEL_ASSIGNED)) {
                int operation = switchingMatrixEntry.getLabelStackOperation();
                if (operation == TSwitchingMatrixEntry.UNDEFINED) {
                    if (isLabeled) {
                        packet.getLabelStack().pushTop(mplsLabel);
                    }
                    discardPacket(packet);
                } else if (operation == TSwitchingMatrixEntry.PUSH_LABEL) {
                    TMPLSLabel mplsLabelAux = new TMPLSLabel();
                    // FIX: Do not use hardcoded values. Use class constants 
                    // instead.
                    mplsLabelAux.setBoS(false);
                    mplsLabelAux.setEXP(0);
                    mplsLabelAux.setLabel(switchingMatrixEntry.getOutgoingLabel());
                    mplsLabelAux.setTTL(packet.getLabelStack().getTop().getTTL() - 1);
                    if (requireBackupLSP) {
                        requestTLDPForBackupLSP(switchingMatrixEntry);
                    }
                    packet.getLabelStack().pushTop(mplsLabelAux);
                    if (isLabeled) {
                        packet.getLabelStack().pushTop(mplsLabel);
                    }
                    TPort outgoingPort = this.ports.getPort(switchingMatrixEntry.getOutgoingPortID());
                    if (isLabeled) {
                        packet.getIPv4Header().getOptionsField().setCrossedActiveNode(this.getIPAddress());
                        this.dmgp.addPacket(packet);
                    }
                    outgoingPort.putPacketOnLink(packet, outgoingPort.getLink().getTargetNodeIDOfTrafficSentBy(this));
                    try {
                        this.generateSimulationEvent(new TSEPacketRouted(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), packet.getSubtype()));
                    } catch (Exception e) {
                        // FIX: This is not a good practice. Avoid.
                        e.printStackTrace();
                    }
                } else if (operation == TSwitchingMatrixEntry.POP_LABEL) {
                    if (packet.getLabelStack().getTop().getBoS()) {
                        TIPv4PDU ipv4Packet = this.createIPv4Packet(packet, switchingMatrixEntry);
                        TPort outgoingPort = this.ports.getPort(switchingMatrixEntry.getOutgoingPortID());
                        outgoingPort.putPacketOnLink(ipv4Packet, outgoingPort.getLink().getTargetNodeIDOfTrafficSentBy(this));
                    } else {
                        packet.getLabelStack().popTop();
                        if (isLabeled) {
                            packet.getLabelStack().pushTop(mplsLabel);
                        }
                        TPort outgoingPort = this.ports.getPort(switchingMatrixEntry.getOutgoingPortID());
                        outgoingPort.putPacketOnLink(packet, outgoingPort.getLink().getTargetNodeIDOfTrafficSentBy(this));
                    }
                    try {
                        this.generateSimulationEvent(new TSEPacketRouted(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), packet.getSubtype()));
                    } catch (Exception e) {
                        // FIX: This is not a good practice. Avoid.
                        e.printStackTrace();
                    }
                } else if (operation == TSwitchingMatrixEntry.SWAP_LABEL) {
                    if (requireBackupLSP) {
                        requestTLDPForBackupLSP(switchingMatrixEntry);
                    }
                    packet.getLabelStack().getTop().setLabel(switchingMatrixEntry.getOutgoingLabel());
                    if (isLabeled) {
                        packet.getLabelStack().pushTop(mplsLabel);
                    }
                    TPort outgoingPort = this.ports.getPort(switchingMatrixEntry.getOutgoingPortID());
                    if (isLabeled) {
                        packet.getIPv4Header().getOptionsField().setCrossedActiveNode(this.getIPAddress());
                        this.dmgp.addPacket(packet);
                    }
                    outgoingPort.putPacketOnLink(packet, outgoingPort.getLink().getTargetNodeIDOfTrafficSentBy(this));
                    try {
                        this.generateSimulationEvent(new TSEPacketRouted(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), packet.getSubtype()));
                    } catch (Exception e) {
                        // FIX: This is not a good practice. Avoid.
                        e.printStackTrace();
                    }
                } else if (operation == TSwitchingMatrixEntry.NOOP) {
                    TPort outgoingPort = this.ports.getPort(switchingMatrixEntry.getOutgoingPortID());
                    outgoingPort.putPacketOnLink(packet, outgoingPort.getLink().getTargetNodeIDOfTrafficSentBy(this));
                    try {
                        this.generateSimulationEvent(new TSEPacketRouted(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), packet.getSubtype()));
                    } catch (Exception e) {
                        // FIX: This is not a good practice. Avoid.
                        e.printStackTrace();
                    }
                }
            } else {
                if (isLabeled) {
                    packet.getLabelStack().pushTop(mplsLabel);
                }
                discardPacket(packet);
            }
        } else {
            if (isLabeled) {
                packet.getLabelStack().pushTop(mplsLabel);
            }
            discardPacket(packet);
        }
    }

    /**
     * This method handles a label request.
     *
     * @param packet Label reuest received from an adjacent node.
     * @param incomingPortID Port of this node from wich the label request has
     * arrived.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void handleTLDPRequest(TTLDPPDU packet, int incomingPortID) {
        TSwitchingMatrixEntry switchingMatrixEntry = null;
        switchingMatrixEntry = this.switchingMatrix.getEntry(packet.getTLDPPayload().getTLDPIdentifier(), incomingPortID);
        if (switchingMatrixEntry == null) {
            switchingMatrixEntry = createEntryFromTLDP(packet, incomingPortID);
        }
        if (switchingMatrixEntry != null) {
            int currentLabel = switchingMatrixEntry.getOutgoingLabel();
            if (currentLabel == TSwitchingMatrixEntry.UNDEFINED) {
                switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.LABEL_REQUESTED);
                this.requestTLDP(switchingMatrixEntry);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                // Do nothing. The Active LER is waiting for a label
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                sendTLDPRequestRefuse(switchingMatrixEntry);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                sendTLDPRequestOk(switchingMatrixEntry);
            } else if (currentLabel == TSwitchingMatrixEntry.REMOVING_LABEL) {
                sendTLDPWithdrawal(switchingMatrixEntry, incomingPortID);
            } else if (currentLabel > 15) {
                sendTLDPRequestOk(switchingMatrixEntry);
            } else {
                discardPacket(packet);
            }
        } else {
            discardPacket(packet);
        }
    }

    /**
     * This method handles a label withdrawal.
     *
     * @param packet Label withdrawal received.
     * @param incomingPortID Port of this node from wich the label withdrawal
     * has arrived.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void handleTLDPWithdrawal(TTLDPPDU packet, int incomingPortID) {
        TSwitchingMatrixEntry switchingMatrixEntry = null;
        if (packet.getLocalOrigin() == TTLDPPDU.CAME_BY_ENTRANCE) {
            switchingMatrixEntry = this.switchingMatrix.getEntry(packet.getTLDPPayload().getTLDPIdentifier(), incomingPortID);
        } else {
            switchingMatrixEntry = this.switchingMatrix.getEntry(packet.getTLDPPayload().getTLDPIdentifier());
        }
        if (switchingMatrixEntry == null) {
            discardPacket(packet);
        } else if (switchingMatrixEntry.getIncomingPortID() == incomingPortID) {
            if (switchingMatrixEntry.getOutgoingLabel() == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                int currentLabel = switchingMatrixEntry.getOutgoingLabel();
                if (currentLabel == TSwitchingMatrixEntry.UNDEFINED) {
                    switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                    sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                    sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getOppositePortID(incomingPortID));
                } else if (currentLabel == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                    switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                    sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                    sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getOppositePortID(incomingPortID));
                } else if (currentLabel == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                    switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                    sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                    sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getOppositePortID(incomingPortID));
                } else if (currentLabel == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                    sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                    this.switchingMatrix.removeEntry(switchingMatrixEntry.getIncomingPortID(), switchingMatrixEntry.getLabelOrFEC(), switchingMatrixEntry.getEntryType());
                } else if (currentLabel == TSwitchingMatrixEntry.REMOVING_LABEL) {
                    sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                    // FIX: Avoid using harcoded values. Use class constants 
                    // instead.
                } else if (currentLabel > 15) {
                    switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                    sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                    sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getOppositePortID(incomingPortID));
                } else {
                    discardPacket(packet);
                }
                if (switchingMatrixEntry.backupLSPHasBeenEstablished() || switchingMatrixEntry.backupLSPShouldBeRemoved()) {
                    int currentBackupLabel = switchingMatrixEntry.getBackupOutgoingLabel();
                    if (currentBackupLabel == TSwitchingMatrixEntry.UNDEFINED) {
                        switchingMatrixEntry.setBackupOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                        sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getBackupOutgoingPortID());
                    } else if (currentBackupLabel == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                        switchingMatrixEntry.setBackupOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                        sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getBackupOutgoingPortID());
                    } else if (currentBackupLabel == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                        switchingMatrixEntry.setBackupOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                        sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getBackupOutgoingPortID());
                    } else if (currentBackupLabel == TSwitchingMatrixEntry.REMOVING_LABEL) {
                        // Do nothing. Waiting for label removal...
                        // FIX: Avoid using harcoded values. Use class constants 
                        // instead.
                    } else if (currentBackupLabel > 15) {
                        switchingMatrixEntry.setBackupOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                        sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getBackupOutgoingPortID());
                    } else {
                        discardPacket(packet);
                    }
                }
            } else {
                sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                this.switchingMatrix.removeEntry(switchingMatrixEntry.getIncomingPortID(), switchingMatrixEntry.getLabelOrFEC(), switchingMatrixEntry.getEntryType());
            }
        } else if (switchingMatrixEntry.getOutgoingPortID() == incomingPortID) {
            int currentLabel = switchingMatrixEntry.getOutgoingLabel();
            if (currentLabel == TSwitchingMatrixEntry.UNDEFINED) {
                switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getIncomingPortID());
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getIncomingPortID());
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getIncomingPortID());
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                this.switchingMatrix.removeEntry(switchingMatrixEntry.getIncomingPortID(), switchingMatrixEntry.getLabelOrFEC(), switchingMatrixEntry.getEntryType());
            } else if (currentLabel == TSwitchingMatrixEntry.REMOVING_LABEL) {
                sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                // FIX: Avoid using harcoded values. Use class constants 
                // instead.
            } else if (currentLabel > 15) {
                if (switchingMatrixEntry.backupLSPHasBeenEstablished()) {
                    sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                    // FIX: Avoid using harcoded values. Use class constants 
                    // instead.
                    if (switchingMatrixEntry.getBackupOutgoingPortID() >= 0) {
                        TInternalLink internalLinkAux = (TInternalLink) ports.getPort(switchingMatrixEntry.getBackupOutgoingPortID()).getLink();
                        internalLinkAux.setLSPUp();
                        internalLinkAux.setBackupLSPDown();
                    }
                    switchingMatrixEntry.switchToBackupLSP();
                } else if (switchingMatrixEntry.getUpstreamTLDPSessionID() != TSwitchingMatrixEntry.UNDEFINED) {
                    switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
                    sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                    sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getIncomingPortID());
                } else {
                    sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                    this.switchingMatrix.removeEntry(switchingMatrixEntry.getIncomingPortID(), switchingMatrixEntry.getLabelOrFEC(), switchingMatrixEntry.getEntryType());
                }
            } else {
                discardPacket(packet);
            }
        } else if (switchingMatrixEntry.getBackupOutgoingPortID() == incomingPortID) {
            int currentBackupLabel = switchingMatrixEntry.getBackupOutgoingLabel();
            if (currentBackupLabel == TSwitchingMatrixEntry.UNDEFINED) {
                sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                switchingMatrixEntry.setBackupOutgoingLabel(TSwitchingMatrixEntry.UNDEFINED);
                switchingMatrixEntry.setBackupOutgoingPortID(TSwitchingMatrixEntry.UNDEFINED);
            } else if (currentBackupLabel == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                switchingMatrixEntry.setBackupOutgoingLabel(TSwitchingMatrixEntry.UNDEFINED);
                switchingMatrixEntry.setBackupOutgoingPortID(TSwitchingMatrixEntry.UNDEFINED);
            } else if (currentBackupLabel == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                switchingMatrixEntry.setBackupOutgoingLabel(TSwitchingMatrixEntry.UNDEFINED);
                switchingMatrixEntry.setBackupOutgoingPortID(TSwitchingMatrixEntry.UNDEFINED);
            } else if (currentBackupLabel == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                // Do nothing.
            } else if (currentBackupLabel == TSwitchingMatrixEntry.REMOVING_LABEL) {
                sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                switchingMatrixEntry.setBackupOutgoingLabel(TSwitchingMatrixEntry.UNDEFINED);
                switchingMatrixEntry.setBackupOutgoingPortID(TSwitchingMatrixEntry.UNDEFINED);
                // FIX: Avoid using harcoded values. Use class constants 
                // instead.
            } else if (currentBackupLabel > 15) {
                sendTLDPWithdrawalOk(switchingMatrixEntry, incomingPortID);
                switchingMatrixEntry.setBackupOutgoingLabel(TSwitchingMatrixEntry.UNDEFINED);
                switchingMatrixEntry.setBackupOutgoingPortID(TSwitchingMatrixEntry.UNDEFINED);
            } else {
                discardPacket(packet);
            }
        }
    }

    /**
     * This method handles a label acknowledge.
     *
     * @param packet Label acknowledge received.
     * @param incomingPortID Port of this node from wich the label acknowledge
     * has arrived.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void handleTLDPRequestOk(TTLDPPDU packet, int incomingPortID) {
        TSwitchingMatrixEntry switchingMatrixEntry = null;
        switchingMatrixEntry = this.switchingMatrix.getEntry(packet.getTLDPPayload().getTLDPIdentifier());
        if (switchingMatrixEntry == null) {
            discardPacket(packet);
        } else if (switchingMatrixEntry.getOutgoingPortID() == incomingPortID) {
            int currentLabel = switchingMatrixEntry.getOutgoingLabel();
            if (currentLabel == TSwitchingMatrixEntry.UNDEFINED) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                switchingMatrixEntry.setOutgoingLabel(packet.getTLDPPayload().getLabel());
                if (switchingMatrixEntry.getLabelOrFEC() == TSwitchingMatrixEntry.UNDEFINED) {
                    switchingMatrixEntry.setLabelOrFEC(this.switchingMatrix.getNewLabel());
                }
                TInternalLink internalLink = (TInternalLink) this.ports.getPort(incomingPortID).getLink();
                if (internalLink != null) {
                    if (switchingMatrixEntry.aBackupLSPHasBeenRequested()) {
                        internalLink.setBackupLSP();
                    } else {
                        internalLink.setLSPUp();
                    }
                }
                sendTLDPRequestOk(switchingMatrixEntry);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.REMOVING_LABEL) {
                discardPacket(packet);
                // FIX: Avoid using harcoded values. Use class constants 
                // instead.
            } else if (currentLabel > 15) {
                discardPacket(packet);
            } else {
                discardPacket(packet);
            }
        } else if (switchingMatrixEntry.getBackupOutgoingPortID() == incomingPortID) {
            int currentBackupLabel = switchingMatrixEntry.getBackupOutgoingLabel();
            if (currentBackupLabel == TSwitchingMatrixEntry.UNDEFINED) {
                discardPacket(packet);
            } else if (currentBackupLabel == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                switchingMatrixEntry.setBackupOutgoingLabel(packet.getTLDPPayload().getLabel());
                if (switchingMatrixEntry.getLabelOrFEC() == TSwitchingMatrixEntry.UNDEFINED) {
                    switchingMatrixEntry.setLabelOrFEC(this.switchingMatrix.getNewLabel());
                }
                TInternalLink internalLink = (TInternalLink) this.ports.getPort(incomingPortID).getLink();
                internalLink.setBackupLSP();
                sendTLDPRequestOk(switchingMatrixEntry);
            } else if (currentBackupLabel == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                discardPacket(packet);
            } else if (currentBackupLabel == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                discardPacket(packet);
            } else if (currentBackupLabel == TSwitchingMatrixEntry.REMOVING_LABEL) {
                discardPacket(packet);
                // FIX: Avoid using harcoded values. Use class constants 
                // instead.
            } else if (currentBackupLabel > 15) {
                discardPacket(packet);
            } else {
                discardPacket(packet);
            }
        }
    }

    /**
     * This method handles a received TLDP packet containing a label refusal.
     *
     * @param packet Label refusal received.
     * @param incomingPortID Port of this node from wich the label refusal has
     * arrived.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void handleTLDPRefuseRequest(TTLDPPDU packet, int incomingPortID) {
        TSwitchingMatrixEntry switchingMatrixEntry = null;
        switchingMatrixEntry = this.switchingMatrix.getEntry(packet.getTLDPPayload().getTLDPIdentifier());
        if (switchingMatrixEntry == null) {
            discardPacket(packet);
        } else if (switchingMatrixEntry.getOutgoingPortID() == incomingPortID) {
            int currentLabel = switchingMatrixEntry.getOutgoingLabel();
            if (currentLabel == TSwitchingMatrixEntry.UNDEFINED) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.LABEL_UNAVAILABLE);
                sendTLDPRequestRefuse(switchingMatrixEntry);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.REMOVING_LABEL) {
                discardPacket(packet);
                // FIX: Avoid using harcoded values. Use class constants 
                // instead.
            } else if (currentLabel > 15) {
                discardPacket(packet);
            } else {
                discardPacket(packet);
            }
        } else if (switchingMatrixEntry.getBackupOutgoingPortID() == incomingPortID) {
            int currentBackupLabel = switchingMatrixEntry.getBackupOutgoingLabel();
            if (currentBackupLabel == TSwitchingMatrixEntry.UNDEFINED) {
                discardPacket(packet);
            } else if (currentBackupLabel == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                switchingMatrixEntry.setBackupOutgoingLabel(TSwitchingMatrixEntry.LABEL_UNAVAILABLE);
                sendTLDPRequestRefuse(switchingMatrixEntry);
            } else if (currentBackupLabel == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                discardPacket(packet);
            } else if (currentBackupLabel == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                discardPacket(packet);
            } else if (currentBackupLabel == TSwitchingMatrixEntry.REMOVING_LABEL) {
                discardPacket(packet);
                // FIX: Avoid using harcoded values. Use class constants 
                // instead.
            } else if (currentBackupLabel > 15) {
                discardPacket(packet);
            } else {
                discardPacket(packet);
            }
        }
    }

    /**
     * This method handles a TLDP packet containing a label withdrawal
     * acknowledgement.
     *
     * @param packet Label withdrawal acknowledgement received.
     * @param incomingPortID Port of this node from wich the label withdrawal
     * acknowledgement has arrived.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void handleTLDPWithdrawalOk(TTLDPPDU packet, int incomingPortID) {
        TSwitchingMatrixEntry switchingMatrixEntry = null;
        if (packet.getLocalOrigin() == TTLDPPDU.CAME_BY_ENTRANCE) {
            switchingMatrixEntry = this.switchingMatrix.getEntry(packet.getTLDPPayload().getTLDPIdentifier(), incomingPortID);
        } else {
            switchingMatrixEntry = this.switchingMatrix.getEntry(packet.getTLDPPayload().getTLDPIdentifier());
        }
        if (switchingMatrixEntry == null) {
            discardPacket(packet);
        } else if (switchingMatrixEntry.getIncomingPortID() == incomingPortID) {
            int currentLabel = switchingMatrixEntry.getOutgoingLabel();
            if (currentLabel == TSwitchingMatrixEntry.UNDEFINED) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                discardPacket(packet);
            } else if ((currentLabel == TSwitchingMatrixEntry.REMOVING_LABEL)
                    || (currentLabel == TSwitchingMatrixEntry.LABEL_WITHDRAWN)) {
                if (switchingMatrixEntry.getOutgoingLabel() != TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                    if (switchingMatrixEntry.getOutgoingLabel() == TSwitchingMatrixEntry.REMOVING_LABEL) {
                        TPort outgoingPortID = this.ports.getPort(switchingMatrixEntry.getOutgoingPortID());
                        if (outgoingPortID != null) {
                            TLink link = outgoingPortID.getLink();
                            if (link.getLinkType() == TLink.INTERNAL) {
                                TInternalLink internalLink = (TInternalLink) link;
                                if (switchingMatrixEntry.aBackupLSPHasBeenRequested()) {
                                    internalLink.setBackupLSPDown();
                                } else {
                                    internalLink.removeLSP();
                                }
                            }
                        }
                        switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.LABEL_WITHDRAWN);
                    }
                }
                this.switchingMatrix.removeEntry(switchingMatrixEntry.getIncomingPortID(), switchingMatrixEntry.getLabelOrFEC(), switchingMatrixEntry.getEntryType());
                if (switchingMatrixEntry.getBackupOutgoingLabel() != TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                    if (switchingMatrixEntry.getBackupOutgoingLabel() == TSwitchingMatrixEntry.REMOVING_LABEL) {
                        if (switchingMatrixEntry.getBackupOutgoingPortID() >= 0) {
                            TPort outgoingPortID = this.ports.getPort(switchingMatrixEntry.getBackupOutgoingPortID());
                            if (outgoingPortID != null) {
                                TLink link = outgoingPortID.getLink();
                                if (link.getLinkType() == TLink.INTERNAL) {
                                    TInternalLink internalLink = (TInternalLink) link;
                                    internalLink.setBackupLSPDown();
                                }
                            }
                        }
                        switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.LABEL_WITHDRAWN);
                    }
                }
                if (switchingMatrixEntry.getIncomingPortID() != TSwitchingMatrixEntry.UNDEFINED) {
                    TPort outgoingPortID = this.ports.getPort(incomingPortID);
                    if (outgoingPortID != null) {
                        TLink link = outgoingPortID.getLink();
                        if (link.getLinkType() == TLink.INTERNAL) {
                            TInternalLink internalLink = (TInternalLink) link;
                            if (switchingMatrixEntry.aBackupLSPHasBeenRequested()) {
                                internalLink.setBackupLSPDown();
                            } else {
                                internalLink.removeLSP();
                            }
                        }
                    }
                }
                this.switchingMatrix.removeEntry(switchingMatrixEntry.getIncomingPortID(), switchingMatrixEntry.getLabelOrFEC(), switchingMatrixEntry.getEntryType());
                // FIX: Avoid using harcoded values. Use class constants 
                // instead.
            } else if (currentLabel > 15) {
                discardPacket(packet);
            } else {
                discardPacket(packet);
            }
        } else if (switchingMatrixEntry.getOutgoingPortID() == incomingPortID) {
            int currentLabel = switchingMatrixEntry.getOutgoingLabel();
            if (currentLabel == TSwitchingMatrixEntry.UNDEFINED) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.REMOVING_LABEL) {
                TPort outgoingPortID = this.ports.getPort(incomingPortID);
                TLink link = outgoingPortID.getLink();
                if (link.getLinkType() == TLink.INTERNAL) {
                    TInternalLink internalLink = (TInternalLink) link;
                    if (switchingMatrixEntry.aBackupLSPHasBeenRequested()) {
                        internalLink.setBackupLSPDown();
                    } else {
                        internalLink.removeLSP();
                    }
                }
                if ((switchingMatrixEntry.getBackupOutgoingLabel() == TSwitchingMatrixEntry.LABEL_WITHDRAWN)
                        || (switchingMatrixEntry.getBackupOutgoingLabel() == TSwitchingMatrixEntry.LABEL_ASSIGNED)) {
                    this.switchingMatrix.removeEntry(switchingMatrixEntry.getIncomingPortID(), switchingMatrixEntry.getLabelOrFEC(), switchingMatrixEntry.getEntryType());
                }
                this.switchingMatrix.removeEntry(switchingMatrixEntry.getIncomingPortID(), switchingMatrixEntry.getLabelOrFEC(), switchingMatrixEntry.getEntryType());
                // FIX: Avoid using harcoded values. Use class constants 
                // instead.
            } else if (currentLabel > 15) {
                discardPacket(packet);
            } else {
                discardPacket(packet);
            }
        } else if (switchingMatrixEntry.getBackupOutgoingPortID() == incomingPortID) {
            int currentLabel = switchingMatrixEntry.getOutgoingLabel();
            if (currentLabel == TSwitchingMatrixEntry.UNDEFINED) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_UNAVAILABLE) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.LABEL_ASSIGNED) {
                discardPacket(packet);
            } else if (currentLabel == TSwitchingMatrixEntry.REMOVING_LABEL) {
                TPort outgoingPortID = this.ports.getPort(incomingPortID);
                TLink link = outgoingPortID.getLink();
                if (link.getLinkType() == TLink.INTERNAL) {
                    TInternalLink internalLink = (TInternalLink) link;
                    if (switchingMatrixEntry.aBackupLSPHasBeenRequested()) {
                        internalLink.setBackupLSPDown();
                    } else {
                        internalLink.removeLSP();
                    }
                }
                if ((switchingMatrixEntry.getOutgoingLabel() == TSwitchingMatrixEntry.LABEL_WITHDRAWN)
                        || (switchingMatrixEntry.getOutgoingLabel() == TSwitchingMatrixEntry.LABEL_ASSIGNED)) {
                    this.switchingMatrix.removeEntry(switchingMatrixEntry.getIncomingPortID(), switchingMatrixEntry.getLabelOrFEC(), switchingMatrixEntry.getEntryType());
                }
                this.switchingMatrix.removeEntry(switchingMatrixEntry.getIncomingPortID(), switchingMatrixEntry.getLabelOrFEC(), switchingMatrixEntry.getEntryType());
                // FIX: Avoid using harcoded values. Use class constants 
                // instead.
            } else if (currentLabel > 15) {
                discardPacket(packet);
            } else {
                discardPacket(packet);
            }
        }
    }

    /**
     * This methods sends a label to a node that is specified in the
     * switching matrix entry specified as an argument.
     *
     * @param switchingMatrixEntry The switching matrix entry specified.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void sendTLDPRequestOk(TSwitchingMatrixEntry switchingMatrixEntry) {
        if (switchingMatrixEntry != null) {
            if (switchingMatrixEntry.getUpstreamTLDPSessionID() != TSwitchingMatrixEntry.UNDEFINED) {
                String localIPAddress = this.getIPAddress();
                String targetIPAddress = this.ports.getIPOfNodeLinkedTo(switchingMatrixEntry.getIncomingPortID());
                if (targetIPAddress != null) {
                    TTLDPPDU newTLDP = null;
                    try {
                        newTLDP = new TTLDPPDU(this.gIdent.getNextID(), localIPAddress, targetIPAddress);
                    } catch (Exception e) {
                        // FIX: this is not a good practice. Avoid.
                        e.printStackTrace();
                    }
                    if (newTLDP != null) {
                        newTLDP.getTLDPPayload().setTLDPMessageType(TTLDPPayload.LABEL_REQUEST_OK);
                        newTLDP.getTLDPPayload().setTargetIPAddress(switchingMatrixEntry.getTailEndIPAddress());
                        newTLDP.getTLDPPayload().setTLDPIdentifier(switchingMatrixEntry.getUpstreamTLDPSessionID());
                        newTLDP.getTLDPPayload().setLabel(switchingMatrixEntry.getLabelOrFEC());
                        if (switchingMatrixEntry.aBackupLSPHasBeenRequested()) {
                            newTLDP.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD_BACKUP);
                        } else {
                            newTLDP.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD);
                        }
                        TPort outgoingPortID = this.ports.getLocalPortConnectedToANodeWithIPAddress(targetIPAddress);
                        outgoingPortID.putPacketOnLink(newTLDP, outgoingPortID.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP, newTLDP.getSize()));
                            this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP));
                        } catch (Exception e) {
                            //FIX: this is not a good practice. Avoid.
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    /**
     * This methods sends a label refusal to a node that is specified in the
     * switching matrix entry specified as an argument.
     *
     * @param switchingMatrixEntry The switching matrix entry specified.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void sendTLDPRequestRefuse(TSwitchingMatrixEntry switchingMatrixEntry) {
        if (switchingMatrixEntry != null) {
            if (switchingMatrixEntry.getUpstreamTLDPSessionID() != TSwitchingMatrixEntry.UNDEFINED) {
                String localIPAddress = this.getIPAddress();
                String targetIPAddress = this.ports.getIPOfNodeLinkedTo(switchingMatrixEntry.getIncomingPortID());
                if (targetIPAddress != null) {
                    TTLDPPDU tldpPacket = null;
                    try {
                        tldpPacket = new TTLDPPDU(this.gIdent.getNextID(), localIPAddress, targetIPAddress);
                    } catch (Exception e) {
                        // FIX: this is not a good practice. Avoid.
                        e.printStackTrace();
                    }
                    if (tldpPacket != null) {
                        tldpPacket.getTLDPPayload().setTLDPMessageType(TTLDPPayload.LABEL_REQUEST_DENIED);
                        tldpPacket.getTLDPPayload().setTargetIPAddress(switchingMatrixEntry.getTailEndIPAddress());
                        tldpPacket.getTLDPPayload().setTLDPIdentifier(switchingMatrixEntry.getUpstreamTLDPSessionID());
                        tldpPacket.getTLDPPayload().setLabel(TSwitchingMatrixEntry.UNDEFINED);
                        if (switchingMatrixEntry.aBackupLSPHasBeenRequested()) {
                            tldpPacket.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD_BACKUP);
                        } else {
                            tldpPacket.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD);
                        }
                        TPort outgoingPortID = this.ports.getLocalPortConnectedToANodeWithIPAddress(targetIPAddress);
                        outgoingPortID.putPacketOnLink(tldpPacket, outgoingPortID.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP, tldpPacket.getSize()));
                            this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP));
                        } catch (Exception e) {
                            // FIX: this is not a good practice. Avoid.
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    /**
     * Este m�todo env�a una confirmaci�n de eliminaci�n de etiqueta al nodo que
     * especifique la correspondiente entrada en la matriz de conmutaci�n.
     *
     * @since 2.0
     * @param portID Puerto por el que se debe enviar la confirmaci�n de
     * eliminaci�n.
     * @param switchingMatrixEntry Entrada de la matriz de conmutaci�n
     * especificada.
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void sendTLDPWithdrawalOk(TSwitchingMatrixEntry switchingMatrixEntry, int portID) {
        if (switchingMatrixEntry != null) {
            String localIPAddress = this.getIPAddress();
            String targetIPAddress = this.ports.getIPOfNodeLinkedTo(portID);
            if (targetIPAddress != null) {
                TTLDPPDU tldpPacket = null;
                try {
                    tldpPacket = new TTLDPPDU(this.gIdent.getNextID(), localIPAddress, targetIPAddress);
                } catch (Exception e) {
                    // FIX: this is not a good practice. Avoid.
                    e.printStackTrace();
                }
                if (tldpPacket != null) {
                    tldpPacket.getTLDPPayload().setTLDPMessageType(TTLDPPayload.LABEL_REVOMAL_REQUEST_OK);
                    tldpPacket.getTLDPPayload().setTargetIPAddress(switchingMatrixEntry.getTailEndIPAddress());
                    tldpPacket.getTLDPPayload().setLabel(TSwitchingMatrixEntry.UNDEFINED);
                    if (switchingMatrixEntry.getOutgoingPortID() == portID) {
                        tldpPacket.getTLDPPayload().setTLDPIdentifier(switchingMatrixEntry.getLocalTLDPSessionID());
                        tldpPacket.setLocalTarget(TTLDPPDU.DIRECTION_FORWARD);
                    } else if (switchingMatrixEntry.getBackupOutgoingPortID() == portID) {
                        tldpPacket.getTLDPPayload().setTLDPIdentifier(switchingMatrixEntry.getLocalTLDPSessionID());
                        tldpPacket.setLocalTarget(TTLDPPDU.DIRECTION_FORWARD);
                    } else if (switchingMatrixEntry.getIncomingPortID() == portID) {
                        tldpPacket.getTLDPPayload().setTLDPIdentifier(switchingMatrixEntry.getUpstreamTLDPSessionID());
                        if (switchingMatrixEntry.aBackupLSPHasBeenRequested()) {
                            tldpPacket.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD_BACKUP);
                        } else {
                            tldpPacket.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD);
                        }
                    }
                    TPort outgoingPortID = this.ports.getPort(portID);
                    outgoingPortID.putPacketOnLink(tldpPacket, outgoingPortID.getLink().getTargetNodeIDOfTrafficSentBy(this));
                    try {
                        this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP, tldpPacket.getSize()));
                        this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP));
                    } catch (Exception e) {
                        // FIX: this is not a good practice. Avoid.
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Este m�todo solicita una etiqueta al nodo que se especifica en la entrada
     * de la matriz de conmutaci�n correspondiente.
     *
     * @param switchingMatrixEntry Entrada en la matriz de conmutaci�n
     * especificada.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void requestTLDP(TSwitchingMatrixEntry switchingMatrixEntry) {
        String localIPAddress = this.getIPAddress();
        String tailEndIPAddress = switchingMatrixEntry.getTailEndIPAddress();
        if (switchingMatrixEntry.getOutgoingLabel() != TSwitchingMatrixEntry.LABEL_ASSIGNED) {
            String nextHopIPAddress = this.topology.getNextHopRABANIPv4Address(localIPAddress, tailEndIPAddress);
            if (nextHopIPAddress != null) {
                TTLDPPDU tldpPacket = null;
                try {
                    tldpPacket = new TTLDPPDU(this.gIdent.getNextID(), localIPAddress, nextHopIPAddress);
                } catch (Exception e) {
                    // FIX: this is not a good practice. Avoid.
                    e.printStackTrace();
                }
                if (tldpPacket != null) {
                    tldpPacket.getTLDPPayload().setTargetIPAddress(tailEndIPAddress);
                    tldpPacket.getTLDPPayload().setTLDPMessageType(TTLDPPayload.LABEL_REQUEST);
                    tldpPacket.getTLDPPayload().setTLDPIdentifier(switchingMatrixEntry.getLocalTLDPSessionID());
                    if (switchingMatrixEntry.aBackupLSPHasBeenRequested()) {
                        tldpPacket.setLSPType(true);
                    } else {
                        tldpPacket.setLSPType(false);
                    }
                    tldpPacket.setLocalTarget(TTLDPPDU.DIRECTION_FORWARD);
                    TPort outgoingPortID = this.ports.getLocalPortConnectedToANodeWithIPAddress(nextHopIPAddress);
                    if (outgoingPortID != null) {
                        outgoingPortID.putPacketOnLink(tldpPacket, outgoingPortID.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP, tldpPacket.getSize()));
                            this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP));
                        } catch (Exception e) {
                            // FIX: this is not a good practice. Avoid.
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    /**
     * Este m�todo solicita una etiqueta al nodo que se especifica en la entrada
     * de la matriz de conmutaci�n correspondiente. La solicitud ir� destinada a
     * crear un LSP de backup.
     *
     * @param switchingMatrixEntry Entrada en la matriz de conmutaci�n
     * especificada.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void requestTLDPForBackupLSP(TSwitchingMatrixEntry switchingMatrixEntry) {
        String localIPAddress = this.getIPAddress();
        String tailEndIPAddress = switchingMatrixEntry.getTailEndIPAddress();
        String nextHopToAvoidIPAddress = this.ports.getIPOfNodeLinkedTo(switchingMatrixEntry.getOutgoingPortID());
        if (nextHopToAvoidIPAddress != null) {
            String nextHopIPAddress = this.topology.getNextHopRABANIPv4Address(localIPAddress, tailEndIPAddress, nextHopToAvoidIPAddress);
            if (nextHopIPAddress != null) {
                if (switchingMatrixEntry.getBackupOutgoingPortID() == TSwitchingMatrixEntry.UNDEFINED) {
                    if (switchingMatrixEntry.getBackupOutgoingLabel() == TSwitchingMatrixEntry.UNDEFINED) {
                        // FIX: Avoid using harcoded values. Use class constants
                        // instead.
                        if (switchingMatrixEntry.getOutgoingLabel() > 15) {
                            switchingMatrixEntry.setBackupOutgoingLabel(TSwitchingMatrixEntry.LABEL_REQUESTED);
                            // FIX: This conditional is redundat as the same 
                            // condition has been tested at the beginning.
                            if (nextHopIPAddress != null) {
                                TTLDPPDU tldpPacket = null;
                                try {
                                    tldpPacket = new TTLDPPDU(this.gIdent.getNextID(), localIPAddress, nextHopIPAddress);
                                } catch (Exception e) {
                                    // FIX: this is ugly. Avoid.
                                    e.printStackTrace();
                                }
                                if (tldpPacket != null) {
                                    tldpPacket.getTLDPPayload().setTargetIPAddress(tailEndIPAddress);
                                    tldpPacket.getTLDPPayload().setTLDPMessageType(TTLDPPayload.LABEL_REQUEST);
                                    tldpPacket.getTLDPPayload().setTLDPIdentifier(switchingMatrixEntry.getLocalTLDPSessionID());
                                    tldpPacket.setLSPType(true);
                                    tldpPacket.setLocalTarget(TTLDPPDU.DIRECTION_FORWARD);
                                    TPort outgoingPortID = this.ports.getLocalPortConnectedToANodeWithIPAddress(nextHopIPAddress);
                                    switchingMatrixEntry.setBackupOutgoingPortID(outgoingPortID.getPortID());
                                    if (outgoingPortID != null) {
                                        outgoingPortID.putPacketOnLink(tldpPacket, outgoingPortID.getLink().getTargetNodeIDOfTrafficSentBy(this));
                                        try {
                                            this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP, tldpPacket.getSize()));
                                            this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP));
                                        } catch (Exception e) {
                                            // FIX: this is ugly. Avoid.
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Este m�todo env�a una eliminaci�n de etiqueta al nodo especificado por le
     * entrada de la matriz de conmutaci�n correspondiente.
     *
     * @since 2.0
     * @param portID Puerto por el que se debe enviar la eliminaci�n.
     * @param switchingMatrixEntry Entrada en la matriz de conmutaci�n
     * especificada.
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void sendTLDPWithdrawal(TSwitchingMatrixEntry switchingMatrixEntry, int portID) {
        if (switchingMatrixEntry != null) {
            switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.REMOVING_LABEL);
            String localIPAddress = this.getIPAddress();
            String tailEndIPAddress = switchingMatrixEntry.getTailEndIPAddress();
            String nextHopIPAddress = this.ports.getIPOfNodeLinkedTo(portID);
            if (nextHopIPAddress != null) {
                TTLDPPDU tldpPacket = null;
                try {
                    tldpPacket = new TTLDPPDU(this.gIdent.getNextID(), localIPAddress, nextHopIPAddress);
                } catch (Exception e) {
                    // FIX: this is ugly. Avoid.
                    e.printStackTrace();
                }
                if (tldpPacket != null) {
                    tldpPacket.getTLDPPayload().setTargetIPAddress(tailEndIPAddress);
                    tldpPacket.getTLDPPayload().setTLDPMessageType(TTLDPPayload.LABEL_REMOVAL_REQUEST);
                    if (switchingMatrixEntry.getOutgoingPortID() == portID) {
                        tldpPacket.getTLDPPayload().setTLDPIdentifier(switchingMatrixEntry.getLocalTLDPSessionID());
                        tldpPacket.setLocalTarget(TTLDPPDU.DIRECTION_FORWARD);
                    } else if (switchingMatrixEntry.getBackupOutgoingPortID() == portID) {
                        tldpPacket.getTLDPPayload().setTLDPIdentifier(switchingMatrixEntry.getLocalTLDPSessionID());
                        tldpPacket.setLocalTarget(TTLDPPDU.DIRECTION_FORWARD);
                    } else if (switchingMatrixEntry.getIncomingPortID() == portID) {
                        tldpPacket.getTLDPPayload().setTLDPIdentifier(switchingMatrixEntry.getUpstreamTLDPSessionID());
                        if (switchingMatrixEntry.aBackupLSPHasBeenRequested()) {
                            tldpPacket.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD_BACKUP);
                        } else {
                            tldpPacket.setLocalTarget(TTLDPPDU.DIRECTION_BACKWARD);
                        }
                    }
                    TPort outgoingPortID = this.ports.getPort(portID);
                    if (outgoingPortID != null) {
                        outgoingPortID.putPacketOnLink(tldpPacket, outgoingPortID.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP, tldpPacket.getSize()));
                            this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP));
                        } catch (Exception e) {
                            // FIX: this is ugly. Avoid.
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    /**
     * Este m�todo reenv�a todas las peticiones pendientes de contestaci�n de
     * una entrada de la matriz de conmutaci�n.
     *
     * @param switchingMatrixEntry Entrada de la matriz de conmutaci�n
     * especificada.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void requestTLDPAfterTimeout(TSwitchingMatrixEntry switchingMatrixEntry) {
        if (switchingMatrixEntry != null) {
            String localIPAddress = this.getIPAddress();
            String tailEndIPAddress = switchingMatrixEntry.getTailEndIPAddress();
            String nextHopIPAddress = this.ports.getIPOfNodeLinkedTo(switchingMatrixEntry.getOutgoingPortID());
            if (nextHopIPAddress != null) {
                TTLDPPDU tldpPacket = null;
                try {
                    tldpPacket = new TTLDPPDU(this.gIdent.getNextID(), localIPAddress, nextHopIPAddress);
                } catch (Exception e) {
                    // FIX: this is ugly. Avoid.
                    e.printStackTrace();
                }
                if (tldpPacket != null) {
                    tldpPacket.getTLDPPayload().setTargetIPAddress(tailEndIPAddress);
                    tldpPacket.getTLDPPayload().setTLDPMessageType(TTLDPPayload.LABEL_REQUEST);
                    tldpPacket.getTLDPPayload().setTLDPIdentifier(switchingMatrixEntry.getLocalTLDPSessionID());
                    if (switchingMatrixEntry.aBackupLSPHasBeenRequested()) {
                        // FIX: Avoid using harcoded values. Use class constants
                        // instead
                        tldpPacket.setLSPType(true);
                    } else {
                        // FIX: Avoid using harcoded values. Use class constants
                        // instead
                        tldpPacket.setLSPType(false);
                    }
                    tldpPacket.setLocalTarget(TTLDPPDU.DIRECTION_FORWARD);
                    TPort outgoingPortID = this.ports.getPort(switchingMatrixEntry.getOutgoingPortID());
                    if (outgoingPortID != null) {
                        outgoingPortID.putPacketOnLink(tldpPacket, outgoingPortID.getLink().getTargetNodeIDOfTrafficSentBy(this));
                        try {
                            this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP, tldpPacket.getSize()));
                            this.generateSimulationEvent(new TSEPacketSent(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), TAbstractPDU.TLDP));
                        } catch (Exception e) {
                            // FIX: this is ugly. Avoid.
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    /**
     * Este m�todo reenv�a todas las eliminaciones de etiquetas pendientes de
     * una entrada de la matriz de conmutaci�n.
     *
     * @since 2.0
     * @param portID Puerto por el que se debe enviar la eliminaci�n.
     * @param switchingMatrixEntry Entrada de la matriz de conmutaci�n
     * especificada.
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void labelWithdrawalAfterTimeout(TSwitchingMatrixEntry switchingMatrixEntry, int portID) {
        sendTLDPWithdrawal(switchingMatrixEntry, portID);
    }

    /**
     * Este m�todo reenv�a todas las eliminaciones de etiquetas pendientes de
     * una entrada de la matriz de conmutaci�n a todos los ports necesarios.
     *
     * @param switchingMatrixEntry Entrada de la matriz de conmutaci�n
     * especificada.
     * @since 2.0
     */
    public void labelWithdrawalAfterTimeout(TSwitchingMatrixEntry switchingMatrixEntry) {
        sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getIncomingPortID());
        sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getOutgoingPortID());
        sendTLDPWithdrawal(switchingMatrixEntry, switchingMatrixEntry.getBackupOutgoingPortID());
    }

    /**
     * Este m�todo decrementa los contadores de retransmisi�n existentes para
     * este nodo.
     *
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public void decreaseCounters() {
        TSwitchingMatrixEntry switchingMatrixEntry = null;
        this.switchingMatrix.getMonitor().lock();
        Iterator entriesIterator = this.switchingMatrix.getEntriesIterator();
        while (entriesIterator.hasNext()) {
            switchingMatrixEntry = (TSwitchingMatrixEntry) entriesIterator.next();
            if (switchingMatrixEntry != null) {
                switchingMatrixEntry.decreaseTimeOut(this.getTickDuration());
                if (switchingMatrixEntry.getOutgoingLabel() == TSwitchingMatrixEntry.LABEL_REQUESTED) {
                    if (switchingMatrixEntry.shouldRetryExpiredTLDPRequest()) {
                        switchingMatrixEntry.resetTimeOut();
                        switchingMatrixEntry.decreaseAttempts();
                        requestTLDPAfterTimeout(switchingMatrixEntry);
                    }
                } else if (switchingMatrixEntry.getOutgoingLabel() == TSwitchingMatrixEntry.REMOVING_LABEL) {
                    if (switchingMatrixEntry.shouldRetryExpiredTLDPRequest()) {
                        switchingMatrixEntry.resetTimeOut();
                        switchingMatrixEntry.decreaseAttempts();
                        labelWithdrawalAfterTimeout(switchingMatrixEntry);
                    } else if (!switchingMatrixEntry.areThereAvailableAttempts()) {
                        entriesIterator.remove();
                    }
                } else {
                    switchingMatrixEntry.resetTimeOut();
                    switchingMatrixEntry.resetAttempts();
                }
            }
        }
        this.switchingMatrix.getMonitor().unLock();
    }

    /**
     * Este m�todo crea una nueva entrada en la matriz de conmutaci�n con los
     * datos de un packet TLDP entrante.
     *
     * @param tldpPacket Paquete TLDP entrante, de solicitud de etiqueta.
     * @param incomingPortID Puerto de entrada del packet TLDP.
     * @return La entrada de la matriz de conmutaci�n, ya creada, insertada e
     * inicializada.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public TSwitchingMatrixEntry createEntryFromTLDP(TTLDPPDU tldpPacket, int incomingPortID) {
        TSwitchingMatrixEntry switchingMatrixEntry = null;
        int predecessorTLDPId = tldpPacket.getTLDPPayload().getTLDPIdentifier();
        TPort incomingPort = this.ports.getPort(incomingPortID);
        String tailEndIPAddress = tldpPacket.getTLDPPayload().getTailEndIPAddress();
        String nextHopIPAddress = this.topology.getNextHopRABANIPv4Address(this.getIPAddress(), tailEndIPAddress);
        if (nextHopIPAddress != null) {
            TPort outgoingPort = this.ports.getLocalPortConnectedToANodeWithIPAddress(nextHopIPAddress);
            int incomingLink = TLink.EXTERNAL;
            int outgoingLink = TLink.INTERNAL;
            switchingMatrixEntry = new TSwitchingMatrixEntry();
            switchingMatrixEntry.setUpstreamTLDPSessionID(predecessorTLDPId);
            switchingMatrixEntry.setTailEndIPAddress(tailEndIPAddress);
            switchingMatrixEntry.setIncomingPortID(incomingPortID);
            switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.UNDEFINED);
            switchingMatrixEntry.setLabelOrFEC(TSwitchingMatrixEntry.UNDEFINED);
            switchingMatrixEntry.setEntryIsForBackupLSP(tldpPacket.getLSPType());
            if (outgoingPort != null) {
                switchingMatrixEntry.setOutgoingPortID(outgoingPort.getPortID());
            } else {
                switchingMatrixEntry.setOutgoingPortID(TSwitchingMatrixEntry.UNDEFINED);
            }
            if (incomingPort != null) {
                incomingLink = incomingPort.getLink().getLinkType();
            }
            if (outgoingPort != null) {
                outgoingLink = outgoingPort.getLink().getLinkType();
            }
            if ((incomingLink == TLink.EXTERNAL) && (outgoingLink == TLink.EXTERNAL)) {
                switchingMatrixEntry.setEntryType(TSwitchingMatrixEntry.FEC_ENTRY);
                switchingMatrixEntry.setLabelStackOperation(TSwitchingMatrixEntry.NOOP);
            } else if ((incomingLink == TLink.EXTERNAL) && (outgoingLink == TLink.INTERNAL)) {
                switchingMatrixEntry.setEntryType(TSwitchingMatrixEntry.FEC_ENTRY);
                switchingMatrixEntry.setLabelStackOperation(TSwitchingMatrixEntry.PUSH_LABEL);
            } else if ((incomingLink == TLink.INTERNAL) && (outgoingLink == TLink.EXTERNAL)) {
                switchingMatrixEntry.setEntryType(TSwitchingMatrixEntry.LABEL_ENTRY);
                switchingMatrixEntry.setLabelStackOperation(TSwitchingMatrixEntry.POP_LABEL);
            } else if ((incomingLink == TLink.INTERNAL) && (outgoingLink == TLink.INTERNAL)) {
                switchingMatrixEntry.setEntryType(TSwitchingMatrixEntry.LABEL_ENTRY);
                switchingMatrixEntry.setLabelStackOperation(TSwitchingMatrixEntry.SWAP_LABEL);
            }
            if (isExitActiveLER(tailEndIPAddress)) {
                switchingMatrixEntry.setLabelOrFEC(this.switchingMatrix.getNewLabel());
                switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.LABEL_ASSIGNED);
                switchingMatrixEntry.setBackupOutgoingLabel(TSwitchingMatrixEntry.LABEL_ASSIGNED);
            }
            try {
                switchingMatrixEntry.setLocalTLDPSessionID(this.gIdentLDP.getNew());
            } catch (Exception e) {
                // FIX: this is ugly. Avoid.
                e.printStackTrace();
            }
            this.switchingMatrix.addEntry(switchingMatrixEntry);
        }
        return switchingMatrixEntry;
    }

    /**
     * Este m�todo crea una nueva entrada en la matriz de conmutaci�n bas�ndose
     * en un packet IPv4 recibido.
     *
     * @param ipv4Packet Paquete IPv4 recibido.
     * @param incomingPortID Puerto por el que ha llegado el packet IPv4.
     * @return La entrada de la matriz de conmutaci�n, creada, insertada e
     * inicializada.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public TSwitchingMatrixEntry createInitialEntryInFECMatrix(TIPv4PDU ipv4Packet, int incomingPortID) {
        TSwitchingMatrixEntry switchingMatrixEntry = null;
        String localIPAddress = this.getIPAddress();
        String tailEndIPAddress = ipv4Packet.getIPv4Header().getTailEndIPAddress();
        String outgoingPortID = this.topology.getNextHopRABANIPv4Address(localIPAddress, tailEndIPAddress);
        if (outgoingPortID != null) {
            TPort incomingPort = this.ports.getPort(incomingPortID);
            TPort outgoingPort = this.ports.getLocalPortConnectedToANodeWithIPAddress(outgoingPortID);
            int incomingLink = TLink.EXTERNAL;
            int outgoingLink = TLink.INTERNAL;
            switchingMatrixEntry = new TSwitchingMatrixEntry();
            switchingMatrixEntry.setUpstreamTLDPSessionID(TSwitchingMatrixEntry.UNDEFINED);
            switchingMatrixEntry.setTailEndIPAddress(tailEndIPAddress);
            switchingMatrixEntry.setIncomingPortID(incomingPortID);
            switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.UNDEFINED);
            switchingMatrixEntry.setLabelOrFEC(classifyPacket(ipv4Packet));
            switchingMatrixEntry.setEntryIsForBackupLSP(false);
            if (outgoingPort != null) {
                switchingMatrixEntry.setOutgoingPortID(outgoingPort.getPortID());
                outgoingLink = outgoingPort.getLink().getLinkType();
            } else {
                switchingMatrixEntry.setOutgoingPortID(TSwitchingMatrixEntry.UNDEFINED);
            }
            if (incomingPort != null) {
                incomingLink = incomingPort.getLink().getLinkType();
            }
            if ((incomingLink == TLink.EXTERNAL) && (outgoingLink == TLink.EXTERNAL)) {
                switchingMatrixEntry.setEntryType(TSwitchingMatrixEntry.FEC_ENTRY);
                switchingMatrixEntry.setLabelStackOperation(TSwitchingMatrixEntry.NOOP);
            } else if ((incomingLink == TLink.EXTERNAL) && (outgoingLink == TLink.INTERNAL)) {
                switchingMatrixEntry.setEntryType(TSwitchingMatrixEntry.FEC_ENTRY);
                switchingMatrixEntry.setLabelStackOperation(TSwitchingMatrixEntry.PUSH_LABEL);
            } else if ((incomingLink == TLink.INTERNAL) && (outgoingLink == TLink.EXTERNAL)) {
                // Not possible
            } else if ((incomingLink == TLink.INTERNAL) && (outgoingLink == TLink.INTERNAL)) {
                // Not possible
            }
            if (isExitActiveLER(tailEndIPAddress)) {
                switchingMatrixEntry.setLabelOrFEC(this.switchingMatrix.getNewLabel());
                switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.LABEL_ASSIGNED);
                switchingMatrixEntry.setBackupOutgoingLabel(TSwitchingMatrixEntry.LABEL_ASSIGNED);
            }
            try {
                switchingMatrixEntry.setLocalTLDPSessionID(this.gIdentLDP.getNew());
            } catch (Exception e) {
                // FIX: this is ugly. Avoid.
                e.printStackTrace();
            }
            this.switchingMatrix.addEntry(switchingMatrixEntry);
        }
        return switchingMatrixEntry;
    }

    /**
     * Este m�todo crea una nueva entrada en la matriz de conmutaci�n bas�ndose
     * en un packet MPLS recibido.
     *
     * @param mplsPacket Paquete MPLS recibido.
     * @param incomingPortID Puerto por el que ha llegado el packet MPLS.
     * @return La entrada de la matriz de conmutaci�n, creada, insertada e
     * inicializada.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public TSwitchingMatrixEntry createInitialEntryInILMMatrix(TMPLSPDU mplsPacket, int incomingPortID) {
        TSwitchingMatrixEntry switchingMatrixEntry = null;
        String localIPAddress = this.getIPAddress();
        String tailEndIPAddress = mplsPacket.getIPv4Header().getTailEndIPAddress();
        String nextHopIPAddress = this.topology.getNextHopRABANIPv4Address(localIPAddress, tailEndIPAddress);
        if (nextHopIPAddress != null) {
            TPort incomingPort = this.ports.getPort(incomingPortID);
            TPort outgoingPort = this.ports.getLocalPortConnectedToANodeWithIPAddress(nextHopIPAddress);
            int incomingLink = TLink.EXTERNAL;
            int outgoingLink = TLink.INTERNAL;
            switchingMatrixEntry = new TSwitchingMatrixEntry();
            switchingMatrixEntry.setUpstreamTLDPSessionID(TSwitchingMatrixEntry.UNDEFINED);
            switchingMatrixEntry.setTailEndIPAddress(tailEndIPAddress);
            switchingMatrixEntry.setIncomingPortID(incomingPortID);
            switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.UNDEFINED);
            switchingMatrixEntry.setEntryIsForBackupLSP(false);
            switchingMatrixEntry.setLabelOrFEC(mplsPacket.getLabelStack().getTop().getLabel());
            if (outgoingPort != null) {
                switchingMatrixEntry.setOutgoingPortID(outgoingPort.getPortID());
                outgoingLink = outgoingPort.getLink().getLinkType();
            } else {
                switchingMatrixEntry.setOutgoingPortID(TSwitchingMatrixEntry.UNDEFINED);
            }
            if (incomingPort != null) {
                incomingLink = incomingPort.getLink().getLinkType();
            }
            if ((incomingLink == TLink.EXTERNAL) && (outgoingLink == TLink.EXTERNAL)) {
                switchingMatrixEntry.setEntryType(TSwitchingMatrixEntry.LABEL_ENTRY);
                switchingMatrixEntry.setLabelStackOperation(TSwitchingMatrixEntry.NOOP);
            } else if ((incomingLink == TLink.EXTERNAL) && (outgoingLink == TLink.INTERNAL)) {
                switchingMatrixEntry.setEntryType(TSwitchingMatrixEntry.LABEL_ENTRY);
                switchingMatrixEntry.setLabelStackOperation(TSwitchingMatrixEntry.PUSH_LABEL);
            } else if ((incomingLink == TLink.INTERNAL) && (outgoingLink == TLink.EXTERNAL)) {
                switchingMatrixEntry.setEntryType(TSwitchingMatrixEntry.LABEL_ENTRY);
                switchingMatrixEntry.setLabelStackOperation(TSwitchingMatrixEntry.POP_LABEL);
            } else if ((incomingLink == TLink.INTERNAL) && (outgoingLink == TLink.INTERNAL)) {
                switchingMatrixEntry.setEntryType(TSwitchingMatrixEntry.LABEL_ENTRY);
                switchingMatrixEntry.setLabelStackOperation(TSwitchingMatrixEntry.SWAP_LABEL);
            }
            if (isExitActiveLER(tailEndIPAddress)) {
                switchingMatrixEntry.setLabelOrFEC(this.switchingMatrix.getNewLabel());
                switchingMatrixEntry.setOutgoingLabel(TSwitchingMatrixEntry.LABEL_ASSIGNED);
                switchingMatrixEntry.setBackupOutgoingLabel(TSwitchingMatrixEntry.LABEL_ASSIGNED);
            }
            try {
                switchingMatrixEntry.setLocalTLDPSessionID(this.gIdentLDP.getNew());
            } catch (Exception e) {
                // FIX: this is ugly. Avoid.
                e.printStackTrace();
            }
            this.switchingMatrix.addEntry(switchingMatrixEntry);
        }
        return switchingMatrixEntry;
    }

    /**
     * Este m�todo toma un packet IPv4 y la entrada de la matriz de conmutaci�n
     * asociada al mismo y crea un packet MPLS etiquetado correctamente que
     * contiene dicho packet IPv4 listo para ser transmitido hacia el interior
     * del dominio.
     *
     * @param ipv4Packet Paquete IPv4 que se debe etiquetar.
     * @param switchingMatrixEntry Entrada de la matriz de conmutaci�n asociada
     * al packet IPv4 que se desea etiquetar.
     * @return El packet IPv4 de entrada, convertido en un packet MPLS
     * correctamente etiquetado.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public TMPLSPDU createMPLSPacket(TIPv4PDU ipv4Packet, TSwitchingMatrixEntry switchingMatrixEntry) {
        TMPLSPDU mplsPacket = null;
        try {
            mplsPacket = new TMPLSPDU(this.gIdent.getNextID(), ipv4Packet.getIPv4Header().getOriginIPAddress(), ipv4Packet.getIPv4Header().getTailEndIPAddress(), ipv4Packet.getSize());
        } catch (EIDGeneratorOverflow e) {
            // FIX: this is ugly. Avoid.
            e.printStackTrace();
        }
        // FIX: At this point, mplsPacket could be null and the next line would
        // throw an exception. Correct.
        mplsPacket.setHeader(ipv4Packet.getIPv4Header());
        mplsPacket.setTCPPayload(ipv4Packet.getTCPPayload());
        if (ipv4Packet.getSubtype() == TAbstractPDU.IPV4) {
            mplsPacket.setSubtype(TAbstractPDU.MPLS);
        } else if (ipv4Packet.getSubtype() == TAbstractPDU.IPV4_GOS) {
            mplsPacket.setSubtype(TAbstractPDU.MPLS_GOS);
        }
        TMPLSLabel mplsLabel = new TMPLSLabel();
        // FIX: all harcoded values should be changed by class constants.
        mplsLabel.setBoS(true);
        mplsLabel.setEXP(0);
        mplsLabel.setLabel(switchingMatrixEntry.getOutgoingLabel());
        mplsLabel.setTTL(ipv4Packet.getIPv4Header().getTTL() - 1);
        mplsPacket.getLabelStack().pushTop(mplsLabel);
        ipv4Packet = null;
        try {
            this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), mplsPacket.getSubtype(), mplsPacket.getSize()));
        } catch (Exception e) {
            // FIX: this is ugly. Avoid.
            e.printStackTrace();
        }
        return mplsPacket;
    }

    /**
     * Este m�todo toma como par�metro un packet MPLS y su entrada en la matriz
     * de conmutaci�n asociada. Extrae del packet MPLS el packet IP
     * correspondiente y actualiza sus elementFields correctamente.
     *
     * @param MPLSPacket Paquete MPLS cuyo contenido de nivel IPv4 se desea
     * extraer.
     * @param switchingMatrixEntry Entrada de la matriz de conmutaci�n asociada
     * al packet MPLS.
     * @return Paquete IPv4 que corresponde al packet MPLS una vez que se ha
     * eliminado toda la informaci�n MLPS; que se ha desetiquetado.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public TIPv4PDU createIPv4Packet(TMPLSPDU MPLSPacket, TSwitchingMatrixEntry switchingMatrixEntry) {
        TIPv4PDU ipv4Packet = null;
        try {
            ipv4Packet = new TIPv4PDU(this.gIdent.getNextID(), MPLSPacket.getIPv4Header().getOriginIPAddress(), MPLSPacket.getIPv4Header().getTailEndIPAddress(), MPLSPacket.getTCPPayload().getSize());
        } catch (EIDGeneratorOverflow e) {
            // FIX: this is ugly. Avoid.
            e.printStackTrace();
        }
        ipv4Packet.setHeader(MPLSPacket.getIPv4Header());
        ipv4Packet.setTCPPayload(MPLSPacket.getTCPPayload());
        ipv4Packet.getIPv4Header().setTTL(MPLSPacket.getLabelStack().getTop().getTTL());
        if (MPLSPacket.getSubtype() == TAbstractPDU.MPLS) {
            ipv4Packet.setSubtype(TAbstractPDU.IPV4);
        } else if (MPLSPacket.getSubtype() == TAbstractPDU.MPLS_GOS) {
            ipv4Packet.setSubtype(TAbstractPDU.IPV4_GOS);
        }
        try {
            this.generateSimulationEvent(new TSEPacketGenerated(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), ipv4Packet.getSubtype(), ipv4Packet.getSize()));
        } catch (Exception e) {
            // FIX: this is ugly. Avoid.
            e.printStackTrace();
        }
        MPLSPacket = null;
        return ipv4Packet;
    }

    /**
     * Este m�todo comprueba si un packet recibido es un packet del interior del
     * dominio MPLS o es un packet externo al mismo.
     *
     * @param packet Paquete que ha llegado al nodo.
     * @param incomingPortID Puerto por el que ha llegado el packet al nodo.
     * @return true, si el packet es exterior al dominio MPLS. false en caso
     * contrario.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public boolean isAnExternalPacket(TAbstractPDU packet, int incomingPortID) {
        if (packet.getType() == TAbstractPDU.IPV4) {
            return true;
        }
        TPort incomingPort = this.ports.getPort(incomingPortID);
        return incomingPort.getLink().getLinkType() == TLink.EXTERNAL;
    }

    /**
     * Este m�todo descarta un packet en el nodo y refleja dicho descarte en las
     * estad�sticas del nodo.
     *
     * @param packet Paquete que se quiere descartar.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    @Override
    public void discardPacket(TAbstractPDU packet) {
        try {
            this.generateSimulationEvent(new TSEPacketDiscarded(this, this.longIdentifierGenerator.getNextID(), this.getAvailableTime(), packet.getSubtype()));
            this.stats.addStatsEntry(packet, TStats.DISCARD);
        } catch (Exception e) {
            // FIX: this is ugly. Avoid.
            e.printStackTrace();
        }
        packet = null;
    }

    /**
     * Este m�todo toma como parametro un packet, supuestamente sin etiquetar, y
     * lo clasifica. Esto significa que determina el FEC_ENTRY al que pertenece
     * el packet. Este valor se calcula como el c�digo HASH practicado a la
     * concatenaci�n de la IP de origen y la IP de destino. En la pr�ctica esto
     * significa que paquetes con el mismo origen y con el mismo destino
     * pertenecer�n al mismo FEC_ENTRY.
     *
     * @param packet El packet que se desea clasificar.
     * @return El FEC_ENTRY al que pertenece el packet pasado por par�metros.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public int classifyPacket(TAbstractPDU packet) {
        String originIPAddress = packet.getIPv4Header().getOriginIPAddress();
        String tailEndIPAddress = packet.getIPv4Header().getTailEndIPAddress();
        String FECString = originIPAddress + tailEndIPAddress;
        // FIX: hashCode() does not have a constistent behaviour between
        // different executions; should be changed and use a persistent 
        // mechanism.
        return FECString.hashCode();
    }

    /**
     * Este m�todo permite el acceso al conjunto de ports del nodo.
     *
     * @return El conjunto de ports del nodo.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    @Override
    public TPortSet getPorts() {
        return this.ports;
    }

    /**
     * Este m�todo calcula si el nodo tiene ports libres o no.
     *
     * @return true, si el nodo tiene ports libres. false en caso contrario.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    @Override
    public boolean hasAvailablePorts() {
        return this.ports.hasAvailablePorts();
    }

    /**
     * Este m�todo calcula el routingWeight del nodo. Se utilizar� para calcular
     * rutas con costo menor. En el nodo LER el pero ser� siempre nulo (cero).
     *
     * @return 0. El routingWeight del LERA.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    @Override
    public long getRoutingWeight() {
        // FIX: All harcoded values should be defined as class constants. They 
        // are weights of the GoS proposal, but anyway, tehy should be 
        // configured as class constans.
        long congestionWeightComponent = (long) (this.ports.getCongestionLevel() * (0.7));
        long switchingMatrixWeightComponent = (long) ((10 * this.switchingMatrix.getNumberOfEntries()) * (0.3));
        long routingWeight = congestionWeightComponent + switchingMatrixWeightComponent;
        return routingWeight;
    }

    /**
     * Este m�todo comprueba si la isntancia actual es el LER de salida del
     * dominio MPLS para una IP dada.
     *
     * @param targetIPAddress IP de destino del tr�fico, para la cual queremos
     * averiguar si el LER es nodo de salida.
     * @return true, si el LER es de salida del dominio para tr�fico dirigido a
     * esa IP. false en caso contrario.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public boolean isExitActiveLER(String targetIPAddress) {
        TPort portAux = this.ports.getLocalPortConnectedToANodeWithIPAddress(targetIPAddress);
        if (portAux != null) {
            if (portAux.getLink().getLinkType() == TLink.EXTERNAL) {
                return true;
            }
        }
        return false;
    }

    /**
     * Este m�todo permite el acceso a la matriz de conmutaci�n de LER.
     *
     * @return La matriz de conmutaci�n del LER.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    public TSwitchingMatrix getSwitchingMatrix() {
        return this.switchingMatrix;
    }

    /**
     * Este m�todo comprueba que la configuraci�n de LER sea la correcta.
     *
     * @return true, si el LER est� bien configurado. false en caso contrario.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    @Override
    public boolean isWellConfigured() {
        return this.wellConfigured;
    }

    /**
     * Este m�todo comprueba que una cierta configuraci�n es v�lida.
     *
     * @param topology Topolog�a a la que pertenece el LER.
     * @param reconfiguration true si se trata de una reconfiguraci�n. false en
     * caso contrario.
     * @return CORRECTA, si la configuraci�n es correcta. Un c�digo de error en
     * caso contrario.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    @Override
    public int validateConfig(TTopology topology, boolean reconfiguration) {
        this.setWellConfigured(false);
        if (this.getName().equals("")) {
            return TActiveLERNode.UNNAMED;
        }
        boolean onlyBlankSpaces = true;
        for (int i = 0; i < this.getName().length(); i++) {
            if (this.getName().charAt(i) != ' ') {
                onlyBlankSpaces = false;
            }
        }
        if (onlyBlankSpaces) {
            return TActiveLERNode.ONLY_BLANK_SPACES;
        }
        if (!reconfiguration) {
            TNode nodeAux = topology.setFirstNodeNamed(this.getName());
            if (nodeAux != null) {
                return TActiveLERNode.NAME_ALREADY_EXISTS;
            }
        } else {
            TNode nodeAux = topology.setFirstNodeNamed(this.getName());
            if (nodeAux != null) {
                if (this.topology.thereIsMoreThanANodeNamed(this.getName())) {
                    return TActiveLERNode.NAME_ALREADY_EXISTS;
                }
            }
        }
        this.setWellConfigured(true);
        return TActiveLERNode.OK;
    }

    /**
     * Este m�todo toma un codigo de error y genera un messageType textual del
     * mismo.
     *
     * @param errorCode El c�digo de error para el cual queremos una explicaci�n
     * textual.
     * @return Cadena de texto explicando el error.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    @Override
    public String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case TActiveLERNode.UNNAMED:
                return (java.util.ResourceBundle.getBundle("simMPLS/lenguajes/lenguajes").getString("TConfigLER.FALTA_NOMBRE"));
            case TActiveLERNode.NAME_ALREADY_EXISTS:
                return (java.util.ResourceBundle.getBundle("simMPLS/lenguajes/lenguajes").getString("TConfigLER.NOMBRE_REPETIDO"));
            case TActiveLERNode.ONLY_BLANK_SPACES:
                return (java.util.ResourceBundle.getBundle("simMPLS/lenguajes/lenguajes").getString("TNodoLER.NombreNoSoloEspacios"));
        }
        return ("");
    }

    /**
     * Este m�todo forma una serializedElement de texto que representa al LER y
     * toda su configuraci�n. Sirve para almacenar el LER en disco.
     *
     * @return Una serializedElement de texto que representa un a este LER.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    @Override
    public String marshall() {
        // FIX: all harcoded values should be coded as class constants.
        String serializedElement = "#LERA#";
        serializedElement += this.getID();
        serializedElement += "#";
        serializedElement += this.getName().replace('#', ' ');
        serializedElement += "#";
        serializedElement += this.getIPAddress();
        serializedElement += "#";
        serializedElement += this.getStatus();
        serializedElement += "#";
        serializedElement += this.getShowName();
        serializedElement += "#";
        serializedElement += this.isGeneratingStats();
        serializedElement += "#";
        serializedElement += this.obtenerPosicion().x;
        serializedElement += "#";
        serializedElement += this.obtenerPosicion().y;
        serializedElement += "#";
        serializedElement += this.switchingPowerInMbps;
        serializedElement += "#";
        serializedElement += this.getPorts().getBufferSizeInMB();
        serializedElement += "#";
        serializedElement += this.dmgp.getDMGPSizeInKB();
        serializedElement += "#";
        return serializedElement;
    }

    /**
     * Este m�todo toma como par�metro una serializedElement de texto que debe
     * pertencer a un LER serializado y configura esta instancia con los
     * elementFields de dicha caddena.
     *
     * @param serializedElement LER serializado.
     * @return true, si no ha habido errores y la instancia actual est� bien
     * configurada. false en caso contrario.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    @Override
    public boolean unMarshall(String serializedElement) {
        // FIX: All numeric values in this method should be implemented as class
        // constants instead of harcodeed values.
        String[] elementFields = serializedElement.split("#");
        if (elementFields.length != 13) {
            return false;
        }
        this.setID(Integer.parseInt(elementFields[2]));
        this.setName(elementFields[3]);
        this.setIPAddress(elementFields[4]);
        this.setStatus(Integer.parseInt(elementFields[5]));
        this.setShowName(Boolean.parseBoolean(elementFields[6]));
        this.setGenerateStats(Boolean.parseBoolean(elementFields[7]));
        int coordX = Integer.parseInt(elementFields[8]);
        int coordY = Integer.parseInt(elementFields[9]);
        this.setPosition(new Point(coordX + 24, coordY + 24));
        this.switchingPowerInMbps = Integer.parseInt(elementFields[10]);
        this.getPorts().setBufferSizeInMB(Integer.parseInt(elementFields[11]));
        this.dmgp.setDMGPSizeInKB(Integer.parseInt(elementFields[12]));
        return true;
    }

    /**
     * Este m�todo permite acceder directamente a las estadisticas del nodo.
     *
     * @return Las estad�sticas del nodo.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    @Override
    public TStats getStats() {
        return this.stats;
    }

    /**
     * Este m�todo permite establecer el n�mero de ports que tendr� el nodo.
     *
     * @param numPorts N�mero de ports deseado para el nodo. Como mucho, 8
     * ports.
     * @since 2.0
     * @author Manuel Domínguez Dorado - ingeniero@ManoloDominguez.com
     */
    @Override
    public synchronized void setPorts(int numPorts) {
        this.ports = new TActivePortSet(numPorts, this);
    }

    // FIX: This values are used to check that the active LER node is correctly
    // configured through the UI. It should not be here but in another place.
    public static final int OK = 0;
    public static final int UNNAMED = 1;
    public static final int NAME_ALREADY_EXISTS = 2;
    public static final int ONLY_BLANK_SPACES = 3;

    private TSwitchingMatrix switchingMatrix;
    private TLongIDGenerator gIdent;
    private TIDGenerator gIdentLDP;
    private int switchingPowerInMbps;
    private TDMGP dmgp;
    private TGPSRPRequestsMatrix gpsrpRequests;
    private TLERAStats stats;
}
