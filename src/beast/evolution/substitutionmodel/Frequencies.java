/*
* File Frequencies.java
*
* Copyright (C) 2010 Remco Bouckaert remco@cs.auckland.ac.nz
*
* This file is part of BEAST2.
* See the NOTICE file distributed with this work for additional
* information regarding copyright ownership and licensing.
*
* BEAST is free software; you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
*  BEAST is distributed in the hope that it will be useful,
*  but WITHOUT ANY WARRANTY; without even the implied warranty of
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*  GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with BEAST; if not, write to the
* Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
* Boston, MA  02110-1301  USA
*/
package beast.evolution.substitutionmodel;

import beast.core.CalculationNode;
import beast.core.Input;
import beast.core.Description;
import beast.core.parameter.RealParameter;
import beast.core.Input.Validate;
import beast.evolution.alignment.Alignment;

// RRB: TODO: make this an interface?

@Description("Represents character frequencies typically used as distribution of the root of the tree. " +
        "Calculates empirical frequencies of characters in sequence data, or simply assumes a uniform " +
        "distribution if the estimate flag is set to false.")
public class Frequencies extends CalculationNode {
    public Input<Alignment> m_data = new Input<Alignment>("data", "Sequence data for which frequencies are calculated");
    public Input<Boolean> m_bEstimate = new Input<Boolean>("estimate", "Whether to estimate the frequencies from data (true=default) or assume a uniform distribution over characters (false)", true);
    public Input<RealParameter> frequencies = new Input<RealParameter>("frequencies", "A set of frequencies specified as space separated values summing to 1", Validate.XOR, m_data);
    
    /** contains frequency distribution **/
    protected double[] m_fFreqs;
    protected double[] stored_fFreqs;
    /** flag to indicate m_fFreqs is up to date **/
    boolean m_bNeedsUpdate;
    boolean store_bNeedsUpdate;
    
    @Override
    public void initAndValidate() throws Exception {
        // sanity check
        double fSum = getSumOfFrequencies(frequencies.get());
        m_fFreqs = new double[frequencies.get().getDimension()];
        stored_fFreqs = new double[frequencies.get().getDimension()];
    	if (Math.abs(fSum-1.0)>1e-6) {
    		throw new Exception("Frequencies do not add up to 1");
    	}
    	update();
    }
    
    /** return up to date frequencies **/
    public double[] getFreqs(){
        //synchronized (this) {
        //System.err.println(m_bNeedsUpdate);
    	    if (m_bNeedsUpdate) {

    		    update();
    	    }
        //}

        /*System.err.println(frequencies.get());
        for (int i = 0; i < m_fFreqs.length; i++) {

            System.err.print(m_fFreqs[i]+" ");
            if(m_fFreqs[i] !=frequencies.get().getValue(i)){
                throw new RuntimeException(""+m_fFreqs[i]);
            }
    		}
        System.err.println();*/
        return m_fFreqs;
    }

    /** recalculate frequencies, unless it is fixed **/
    void update() {
        if (frequencies.get() != null) {
            //System.out.println("Get values from here");
        	// if user specified, parse frequencies from space delimited string


    		for (int i = 0; i < m_fFreqs.length; i++) {
    			m_fFreqs[i] = frequencies.get().getValue(i);
    		}


    	}else if (m_bEstimate.get()) { // if not user specified, either estimate from data or set as fixed
    		// estimate
            estimateFrequencies();
            checkFrequencies();
        } else {
    		// uniformly distributed
            int nStates = m_data.get().getMaxStateCount();
            m_fFreqs = new double[nStates];
            for (int i = 0; i < nStates; i++) {
                m_fFreqs[i] = 1.0 / nStates;
            }
        }
    	m_bNeedsUpdate = false;
    } // update


    /** estimate from sequence alignment **/
    void estimateFrequencies() {
        Alignment alignment = m_data.get();
        m_fFreqs = new double[alignment.getMaxStateCount()];
        for (int i = 0; i < alignment.getPatternCount(); i++) {
            int[] nPattern = alignment.getPattern(i);
            int nWeight = alignment.getPatternWeight(i);
            for (int iValue : nPattern) {
                if (iValue < m_fFreqs.length) { // ignore unknowns
                    m_fFreqs[iValue] += nWeight;
                }
            }
        }
        // normalize
        double fSum = 0;
        for (double f : m_fFreqs) {
            fSum += f;
        }
        for (int i = 0; i < m_fFreqs.length; i++) {
            m_fFreqs[i] /= fSum;
        }
    } // calcFrequencies

    /**
     * Ensures that frequencies are not smaller than MINFREQ and
     * that two frequencies differ by at least 2*MINFDIFF.
     * This avoids potential problems later when eigenvalues
     * are computed.
     */
    private void checkFrequencies() {
        // required frequency difference
        double MINFDIFF = 1.0E-10;

        // lower limit on frequency
        double MINFREQ = 1.0E-10;

        int maxi = 0;
        double sum = 0.0;
        double maxfreq = 0.0;
        for (int i = 0; i < m_fFreqs.length; i++) {
            double freq = m_fFreqs[i];
            if (freq < MINFREQ) m_fFreqs[i] = MINFREQ;
            if (freq > maxfreq) {
                maxfreq = freq;
                maxi = i;
            }
            sum += m_fFreqs[i];
        }
        double diff = 1.0 - sum;
        m_fFreqs[maxi] += diff;

        for (int i = 0; i < m_fFreqs.length - 1; i++) {
            for (int j = i + 1; j < m_fFreqs.length; j++) {
                if (m_fFreqs[i] == m_fFreqs[j]) {
                    m_fFreqs[i] += MINFDIFF;
                    m_fFreqs[j] += MINFDIFF;
                }
            }
        }
    } // checkFrequencies
    
    /** CalculationNode implementation **/
    @Override
    protected boolean requiresRecalculation() {
        boolean recalculates = false;
        if(frequencies.get().somethingIsDirty()){

    	    m_bNeedsUpdate = true;
            recalculates = true;
        }
        //System.err.println("requiresRC: "+m_bNeedsUpdate);
    	return recalculates;
    }

/**
     * @param frequencies the frequencies
     * @return return the sum of frequencies
     */
    private double getSumOfFrequencies(RealParameter frequencies) {
        double total = 0.0;
        for (int i = 0; i < frequencies.getDimension(); i++) {
            total += frequencies.getValue(i);
        }
        return total;
    }

    public void store(){
        System.arraycopy(m_fFreqs, 0, stored_fFreqs, 0, stored_fFreqs.length);
        super.store();
    }

    public void restore(){
        double[] tmp = stored_fFreqs;
        stored_fFreqs = m_fFreqs;
        m_fFreqs = tmp;
        super.restore();
    }
    
} // class Frequencies
