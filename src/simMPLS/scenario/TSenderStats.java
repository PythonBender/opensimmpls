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

import simMPLS.protocols.TAbstractPDU;
import org.jfree.chart.*;
import org.jfree.chart.labels.*;
import org.jfree.chart.plot.*;
import org.jfree.data.*;

/**
 * Esta clase implementa las estad�sticas de un nodo emisor.
 * @author <B>Manuel Dom�nguez Dorado</B><br><A
 * href="mailto:ingeniero@ManoloDominguez.com">ingeniero@ManoloDominguez.com</A><br><A href="http://www.ManoloDominguez.com" target="_blank">http://www.ManoloDominguez.com</A>
 * @version 1.0
 */
public class TSenderStats extends TStats {
    
    /**
     * Creates a new instance of TEstadisticasEmisor
     * @since 2.0
     */
    public TSenderStats() {
    	paquetesSalientes = new XYSeriesCollection();
    	paquetesDescartados = new XYSeriesCollection();
    	salientesIPv4 = new XYSeries(TStats.IPV4);
    	salientesIPv4_GOS1 = new XYSeries(TStats.IPV4_GOS1);
    	salientesIPv4_GOS2 = new XYSeries(TStats.IPV4_GOS2);
    	salientesIPv4_GOS3 = new XYSeries(TStats.IPV4_GOS3);
    	salientesMPLS = new XYSeries(TStats.MPLS);
    	salientesMPLS_GOS1 = new XYSeries(TStats.MPLS_GOS1);
    	salientesMPLS_GOS2 = new XYSeries(TStats.MPLS_GOS2);
    	salientesMPLS_GOS3 = new XYSeries(TStats.MPLS_GOS3);
    	descartadosIPv4 = new XYSeries(TStats.IPV4);
    	descartadosIPv4_GOS1 = new XYSeries(TStats.IPV4_GOS1);
    	descartadosIPv4_GOS2 = new XYSeries(TStats.IPV4_GOS2);
    	descartadosIPv4_GOS3 = new XYSeries(TStats.IPV4_GOS3);
	descartadosMPLS = new XYSeries(TStats.MPLS);
	descartadosMPLS_GOS1 = new XYSeries(TStats.MPLS_GOS1);
	descartadosMPLS_GOS2 = new XYSeries(TStats.MPLS_GOS2);
	descartadosMPLS_GOS3 = new XYSeries(TStats.MPLS_GOS3);
        tSIPV4 = 0;
        tSIPV4_GOS1 = 0;
        tSIPV4_GOS2 = 0;
        tSIPV4_GOS3 = 0;
        tSMPLS = 0;
        tSMPLS_GOS1 = 0;
        tSMPLS_GOS2 = 0;
        tSMPLS_GOS3 = 0;
        tDIPV4 = 0;
        tDIPV4_GOS1 = 0;
        tDIPV4_GOS2 = 0;
        tDIPV4_GOS3 = 0;
        tDMPLS = 0;
        tDMPLS_GOS1 = 0;
        tDMPLS_GOS2 = 0;
        tDMPLS_GOS3 = 0;
    }
    
    /**
     * Este m�todo obtiene los datos que permitiran generar la gr�fica 1.
     * @return Datos dela gr�fica 1.
     * @since 2.0
     */
    public org.jfree.data.AbstractDataset obtenerDatosGrafica1() {
        return this.paquetesSalientes;
    }
    
    /**
     * Este m�todo obtiene los datos que permitiran generar la gr�fica 2.
     * @return Datos dela gr�fica 2.
     * @since 2.0
     */    public org.jfree.data.AbstractDataset obtenerDatosGrafica2() {
        return this.paquetesDescartados;
    }
    
    /**
     * Este m�todo obtiene los datos que permitiran generar la gr�fica 3.
     * @return Datos dela gr�fica 3.
     * @since 2.0
     */    public org.jfree.data.AbstractDataset obtenerDatosGrafica3() {
        return null;
    }
    
    /**
     * Este m�todo obtiene los datos que permitiran generar la gr�fica 4.
     * @return Datos dela gr�fica 4.
     * @since 2.0
     */    public org.jfree.data.AbstractDataset obtenerDatosGrafica4() {
        return null;
    }
    
    /**
     * Este m�todo obtiene los datos que permitiran generar la gr�fica 5.
     * @return Datos dela gr�fica 5.
     * @since 2.0
     */    public org.jfree.data.AbstractDataset obtenerDatosGrafica5() {
        return null;
    }
    
    /**
     * Este m�todo obtiene los datos que permitiran generar la gr�fica 6.
     * @return Datos dela gr�fica 6.
     * @since 2.0
     */    public org.jfree.data.AbstractDataset obtenerDatosGrafica6() {
        return null;
    }

    /**
     * Este m�todo amplia las estad�sticas, a�adiendo las del nuevo paquete
     * especificado.
     * @param paquete Paquete que se desea contabilizar en las estad�sticas.
     * @param entrada ENTRADA, SALIDA o DISCARD, dependiendo de si el paquete entra en el nodo, sale
 de �l o es descartado.
     * @since 2.0
     */    
    public void addStatsEntry(TAbstractPDU paquete, int entrada) {
        if (this.estadisticasActivas) {
            int tipoPaquete = paquete.getSubtype();
            int GoS = 0;
            if (tipoPaquete == TAbstractPDU.MPLS) {
                if (entrada == TStats.SALIDA) {
                    this.tSMPLS++;
                } else if (entrada == TStats.DISCARD) {
                    this.tDMPLS++;
                }
            } else if (tipoPaquete == TAbstractPDU.MPLS_GOS) {
                GoS = paquete.getIPv4Header().getOptionsField().getRequestedGoSLevel();
                if (entrada == TStats.SALIDA) {
                    if ((GoS == TAbstractPDU.EXP_LEVEL0_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL0_WITH_BACKUP_LSP)) {
                        this.tSMPLS++;
                    } else if ((GoS == TAbstractPDU.EXP_LEVEL1_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL1_WITH_BACKUP_LSP)) {
                        this.tSMPLS_GOS1++;
                    } else if ((GoS == TAbstractPDU.EXP_LEVEL2_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL2_WITH_BACKUP_LSP)) {
                        this.tSMPLS_GOS2++;
                    } else if ((GoS == TAbstractPDU.EXP_LEVEL3_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL3_WITH_BACKUP_LSP)) {
                        this.tSMPLS_GOS3++;
                    }
                } else if (entrada == TStats.DISCARD) {
                    if ((GoS == TAbstractPDU.EXP_LEVEL0_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL0_WITH_BACKUP_LSP)) {
                        this.tDMPLS++;
                    } else if ((GoS == TAbstractPDU.EXP_LEVEL1_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL1_WITH_BACKUP_LSP)) {
                        this.tDMPLS_GOS1++;
                    } else if ((GoS == TAbstractPDU.EXP_LEVEL2_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL2_WITH_BACKUP_LSP)) {
                        this.tDMPLS_GOS2++;
                    } else if ((GoS == TAbstractPDU.EXP_LEVEL3_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL3_WITH_BACKUP_LSP)) {
                        this.tDMPLS_GOS3++;
                    }
                }
            } else if (tipoPaquete == TAbstractPDU.IPV4) {
                if (entrada == TStats.SALIDA) {
                    this.tSIPV4++;
                } else if (entrada == TStats.DISCARD) {
                    this.tDIPV4++;
                }
            } else if (tipoPaquete == TAbstractPDU.IPV4_GOS) {
                GoS = paquete.getIPv4Header().getOptionsField().getRequestedGoSLevel();
                if (entrada == TStats.SALIDA) {
                    if ((GoS == TAbstractPDU.EXP_LEVEL0_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL0_WITH_BACKUP_LSP)) {
                        this.tSIPV4++;
                    } else if ((GoS == TAbstractPDU.EXP_LEVEL1_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL1_WITH_BACKUP_LSP)) {
                        this.tSIPV4_GOS1++;
                    } else if ((GoS == TAbstractPDU.EXP_LEVEL2_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL2_WITH_BACKUP_LSP)) {
                        this.tSIPV4_GOS2++;
                    } else if ((GoS == TAbstractPDU.EXP_LEVEL3_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL3_WITH_BACKUP_LSP)) {
                        this.tSIPV4_GOS3++;
                    }
                } else if (entrada == TStats.DISCARD) {
                    if ((GoS == TAbstractPDU.EXP_LEVEL0_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL0_WITH_BACKUP_LSP)) {
                        this.tDIPV4++;
                    } else if ((GoS == TAbstractPDU.EXP_LEVEL1_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL1_WITH_BACKUP_LSP)) {
                        this.tDIPV4_GOS1++;
                    } else if ((GoS == TAbstractPDU.EXP_LEVEL2_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL2_WITH_BACKUP_LSP)) {
                        this.tDIPV4_GOS2++;
                    } else if ((GoS == TAbstractPDU.EXP_LEVEL3_WITHOUT_BACKUP_LSP) || (GoS == TAbstractPDU.EXP_LEVEL3_WITH_BACKUP_LSP)) {
                        this.tDIPV4_GOS3++;
                    }
                }
            }
        }
    }
    
    /**
     * Este m�todo devuelve el n�mero de gr�fica que tiene el emisor.
     * @return N�mero de gr�ficas del emisor.
     * @since 2.0
     */    
    public int obtenerNumeroGraficas() {
        return 2;
    }
    
    /**
     * Este m�todo restaura las estad�sticas del emisor a su valor original com osi
     * acabasen de se creadas por el cosntructor.
     * @since 2.0
     */    
    public void reset() {
    	paquetesSalientes = new XYSeriesCollection();
    	paquetesDescartados = new XYSeriesCollection();
    	salientesIPv4 = new XYSeries(TStats.IPV4);
    	salientesIPv4_GOS1 = new XYSeries(TStats.IPV4_GOS1);
    	salientesIPv4_GOS2 = new XYSeries(TStats.IPV4_GOS2);
    	salientesIPv4_GOS3 = new XYSeries(TStats.IPV4_GOS3);
    	salientesMPLS = new XYSeries(TStats.MPLS);
    	salientesMPLS_GOS1 = new XYSeries(TStats.MPLS_GOS1);
    	salientesMPLS_GOS2 = new XYSeries(TStats.MPLS_GOS2);
    	salientesMPLS_GOS3 = new XYSeries(TStats.MPLS_GOS3);
    	descartadosIPv4 = new XYSeries(TStats.IPV4);
    	descartadosIPv4_GOS1 = new XYSeries(TStats.IPV4_GOS1);
    	descartadosIPv4_GOS2 = new XYSeries(TStats.IPV4_GOS2);
    	descartadosIPv4_GOS3 = new XYSeries(TStats.IPV4_GOS3);
	descartadosMPLS = new XYSeries(TStats.MPLS);
	descartadosMPLS_GOS1 = new XYSeries(TStats.MPLS_GOS1);
	descartadosMPLS_GOS2 = new XYSeries(TStats.MPLS_GOS2);
	descartadosMPLS_GOS3 = new XYSeries(TStats.MPLS_GOS3);
        tSIPV4 = 0;
        tSIPV4_GOS1 = 0;
        tSIPV4_GOS2 = 0;
        tSIPV4_GOS3 = 0;
        tSMPLS = 0;
        tSMPLS_GOS1 = 0;
        tSMPLS_GOS2 = 0;
        tSMPLS_GOS3 = 0;
        tDIPV4 = 0;
        tDIPV4_GOS1 = 0;
        tDIPV4_GOS2 = 0;
        tDIPV4_GOS3 = 0;
        tDMPLS = 0;
        tDMPLS_GOS1 = 0;
        tDMPLS_GOS2 = 0;
        tDMPLS_GOS3 = 0;
    }
    
    /**
     * Este m�todo actualiza las estadisticas con los valores nuevos desde la ultima
     * vez que se llam� a este m�todo.
     * @param instante Tic de reloj al que se a�adir�n los �ltimos datos.
     * @since 2.0
     */    
    public void consolidateData(long instante) {
        if (this.estadisticasActivas) {
            if (tSIPV4 > 0) {
		if (salientesIPv4.getItemCount() == 0) {
                    this.salientesIPv4.add(instante-1, 0);
                    this.salientesIPv4.add(instante, tSIPV4);
                    this.paquetesSalientes.addSeries(salientesIPv4);
                } else {
                    this.salientesIPv4.add(instante, tSIPV4);
                }
            } 
            
            if (tSIPV4_GOS1 > 0) {
                if (salientesIPv4_GOS1.getItemCount() == 0) {
                    this.salientesIPv4_GOS1.add(instante-1, 0);
                    this.salientesIPv4_GOS1.add(instante, tSIPV4_GOS1);
                    this.paquetesSalientes.addSeries(salientesIPv4_GOS1);
                } else {
                    this.salientesIPv4_GOS1.add(instante, tSIPV4_GOS1);
                }
            }
            
            if (tSIPV4_GOS2 > 0) {
                if (salientesIPv4_GOS2.getItemCount() == 0) {
                    this.salientesIPv4_GOS2.add(instante-1, 0);
                    this.salientesIPv4_GOS2.add(instante, tSIPV4_GOS2);
                    this.paquetesSalientes.addSeries(salientesIPv4_GOS2);
                } else {
                    this.salientesIPv4_GOS2.add(instante, tSIPV4_GOS2);
                }
            }
            
            if (tSIPV4_GOS3 > 0) {
                if (salientesIPv4_GOS3.getItemCount() == 0) {
                    this.salientesIPv4_GOS3.add(instante-1, 0);
                    this.salientesIPv4_GOS3.add(instante, tSIPV4_GOS3);
                    this.paquetesSalientes.addSeries(salientesIPv4_GOS3);
                } else {
                    this.salientesIPv4_GOS3.add(instante, tSIPV4_GOS3);
                }
            }
            
            if (tSMPLS > 0) {
                if (salientesMPLS.getItemCount() == 0) {
                    this.salientesMPLS.add(instante-1, 0);
                    this.salientesMPLS.add(instante, tSMPLS);
                    this.paquetesSalientes.addSeries(salientesMPLS);
                } else {
                    this.salientesMPLS.add(instante, tSMPLS);
                }
            }
            
            if (tSMPLS_GOS1 > 0) {
                if (salientesMPLS_GOS1.getItemCount() == 0) {
                    this.salientesMPLS_GOS1.add(instante-1, 0);
                    this.salientesMPLS_GOS1.add(instante, tSMPLS_GOS1);
                    this.paquetesSalientes.addSeries(salientesMPLS_GOS1);
                } else {
                    this.salientesMPLS_GOS1.add(instante, tSMPLS_GOS1);
                }
            }
            
            if (tSMPLS_GOS2 > 0) {
                if (salientesMPLS_GOS2.getItemCount() == 0) {
                    this.salientesMPLS_GOS2.add(instante-1, 0);
                    this.salientesMPLS_GOS2.add(instante, tSMPLS_GOS2);
                    this.paquetesSalientes.addSeries(salientesMPLS_GOS2);
                } else {
                    this.salientesMPLS_GOS2.add(instante, tSMPLS_GOS2);
                }
            }
            
            if (tSMPLS_GOS3 > 0) {
                if (salientesMPLS_GOS3.getItemCount() == 0) {
                    this.salientesMPLS_GOS3.add(instante-1, 0);
                    this.salientesMPLS_GOS3.add(instante, tSMPLS_GOS3);
                    this.paquetesSalientes.addSeries(salientesMPLS_GOS3);
                } else {
                    this.salientesMPLS_GOS3.add(instante, tSMPLS_GOS3);
                }
            }
            
            if (tDIPV4 > 0) {
                if (descartadosIPv4.getItemCount() == 0) {
                    this.descartadosIPv4.add(instante-1, 0);
                    this.descartadosIPv4.add(instante, tDIPV4);
                    this.paquetesDescartados.addSeries(descartadosIPv4);
                } else {
                    this.descartadosIPv4.add(instante, tDIPV4);
                }
            }
            
            if (tDIPV4_GOS1 > 0) {
                if (descartadosIPv4_GOS1.getItemCount() == 0) {
                    this.descartadosIPv4_GOS1.add(instante-1, 0);
                    this.descartadosIPv4_GOS1.add(instante, tDIPV4_GOS1);
                    this.paquetesDescartados.addSeries(descartadosIPv4_GOS1);
                } else {
                    this.descartadosIPv4_GOS1.add(instante, tDIPV4_GOS1);
                }
            }

            if (tDIPV4_GOS2 > 0) {
                if (descartadosIPv4_GOS2.getItemCount() == 0) {
                    this.descartadosIPv4_GOS2.add(instante-1, 0);
                    this.descartadosIPv4_GOS2.add(instante, tDIPV4_GOS2);
                    this.paquetesDescartados.addSeries(descartadosIPv4_GOS2);
                } else {
                    this.descartadosIPv4_GOS2.add(instante, tDIPV4_GOS2);
                }
            }

            if (tDIPV4_GOS3 > 0) {
                if (descartadosIPv4_GOS3.getItemCount() == 0) {
                    this.descartadosIPv4_GOS3.add(instante-1, 0);
                    this.descartadosIPv4_GOS3.add(instante, tDIPV4_GOS3);
                    this.paquetesDescartados.addSeries(descartadosIPv4_GOS3);
                } else {
                    this.descartadosIPv4_GOS3.add(instante, tDIPV4_GOS3);
                }
            }
            
            if (tDMPLS > 0) {
                if (descartadosMPLS.getItemCount() == 0) {
                    this.descartadosMPLS.add(instante-1, 0);
                    this.descartadosMPLS.add(instante, tDMPLS);
                    this.paquetesDescartados.addSeries(descartadosMPLS);
                } else {
                    this.descartadosMPLS.add(instante, tDMPLS);
                }
            }
            
            if (tDMPLS_GOS1 > 0) {
                if (descartadosMPLS_GOS1.getItemCount() == 0) {
                    this.descartadosMPLS_GOS1.add(instante-1, 0);
                    this.descartadosMPLS_GOS1.add(instante, tDMPLS_GOS1);
                    this.paquetesDescartados.addSeries(descartadosMPLS_GOS1);
                } else {
                    this.descartadosMPLS_GOS1.add(instante, tDMPLS_GOS1);
                }
            }
            
            if (tDMPLS_GOS2 > 0) {
                if (descartadosMPLS_GOS2.getItemCount() == 0) {
                    this.descartadosMPLS_GOS2.add(instante-1, 0);
                    this.descartadosMPLS_GOS2.add(instante, tDMPLS_GOS2);
                    this.paquetesDescartados.addSeries(descartadosMPLS_GOS2);
                } else {
                    this.descartadosMPLS_GOS2.add(instante, tDMPLS_GOS2);
                }
            }
            
            if (tDMPLS_GOS3 > 0) {
                if (descartadosMPLS_GOS3.getItemCount() == 0) {
                    this.descartadosMPLS_GOS3.add(instante-1, 0);
                    this.descartadosMPLS_GOS3.add(instante, tDMPLS_GOS3);
                    this.paquetesDescartados.addSeries(descartadosMPLS_GOS3);
                } else {
                    this.descartadosMPLS_GOS3.add(instante, tDMPLS_GOS3);
                }
            }
        }
    }    
    
    /**
     * Este m�todo obtiene el t�tulo de la gr�fica 1.
     * @return T�tulo de la gr�fica 1.
     * @since 2.0
     */    
    public String obtenerTitulo1() {
        return TStats.PAQUETES_SALIENTES;
    }
    
    /**
     * Este m�todo obtiene el t�tulo de la gr�fica 2.
     * @return T�tulo de la gr�fica 2.
     * @since 2.0
     */    
    public String obtenerTitulo2() {
        return TStats.PAQUETES_DESCARTADOS;
    }
    
    /**
     * Este m�todo obtiene el t�tulo de la gr�fica 3.
     * @return T�tulo de la gr�fica 3.
     * @since 2.0
     */    
    public String obtenerTitulo3() {
        return null;
    }
    
    /**
     * Este m�todo obtiene el t�tulo de la gr�fica 4.
     * @return T�tulo de la gr�fica 4.
     * @since 2.0
     */    
    public String obtenerTitulo4() {
        return null;
    }
    
    /**
     * Este m�todo obtiene el t�tulo de la gr�fica 5.
     * @return T�tulo de la gr�fica 5.
     * @since 2.0
     */    
    public String obtenerTitulo5() {
        return null;
    }
    
    /**
     * Este m�todo obtiene el t�tulo de la gr�fica 6.
     * @return T�tulo de la gr�fica 6.
     * @since 2.0
     */    
    public String obtenerTitulo6() {
        return null;
    }
    
    private int tSIPV4;
    private int tSIPV4_GOS1;
    private int tSIPV4_GOS2;
    private int tSIPV4_GOS3;
    private int tSMPLS;
    private int tSMPLS_GOS1;
    private int tSMPLS_GOS2;
    private int tSMPLS_GOS3;
    private int tDIPV4;
    private int tDIPV4_GOS1;
    private int tDIPV4_GOS2;
    private int tDIPV4_GOS3;
    private int tDMPLS;
    private int tDMPLS_GOS1;
    private int tDMPLS_GOS2;
    private int tDMPLS_GOS3;
    private XYSeriesCollection paquetesSalientes;
    private XYSeriesCollection paquetesDescartados;
    private XYSeries salientesIPv4;
    private XYSeries salientesIPv4_GOS1;
    private XYSeries salientesIPv4_GOS2;
    private XYSeries salientesIPv4_GOS3;
    private XYSeries salientesMPLS;
    private XYSeries salientesMPLS_GOS1;
    private XYSeries salientesMPLS_GOS2;
    private XYSeries salientesMPLS_GOS3;
    private XYSeries descartadosIPv4;
    private XYSeries descartadosIPv4_GOS1;
    private XYSeries descartadosIPv4_GOS2;
    private XYSeries descartadosIPv4_GOS3;
    private XYSeries descartadosMPLS;
    private XYSeries descartadosMPLS_GOS1;
    private XYSeries descartadosMPLS_GOS2;
    private XYSeries descartadosMPLS_GOS3;
}
