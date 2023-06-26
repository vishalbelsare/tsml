/*
 * This file is part of the UEA Time Series Machine Learning (TSML) toolbox.
 *
 * The UEA TSML toolbox is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published 
 * by the Free Software Foundation, either version 3 of the License, or 
 * (at your option) any later version.
 *
 * The UEA TSML toolbox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with the UEA TSML toolbox. If not, see <https://www.gnu.org/licenses/>.
 */
 
package machine_learning.classifiers.ensembles.weightings;

import machine_learning.classifiers.ensembles.AbstractEnsemble.EnsembleModule;

import java.io.Serializable;

/**
 * Base class for defining the weighting of a classifiers votes in ensemble classifiers
 * 
 * @author James Large
 */
public abstract class ModuleWeightingScheme implements Serializable {
    
    public boolean uniformWeighting = true;
    public boolean needTrainPreds = true;
    
    public void defineWeightings(EnsembleModule[] modules, int numClasses) {
        for (EnsembleModule m : modules) //by default, sets weights independently for each module
            m.posteriorWeights = defineWeighting(m, numClasses);
        
        //some schemes may sets weights for each moduel relative to the rest, and 
        //so will need to override this method
    }
    
    protected abstract double[] defineWeighting(EnsembleModule trainPredictions, int numClasses);
    
    protected double[] makeUniformWeighting(double weight, int numClasses) {
        //Prevents all weights from being set to 0 for datasets such as Fungi.
        if (weight == 0) weight = 1;

        double[] weights = new double[numClasses];
        for (int i = 0; i < weights.length; ++i)
            weights[i] = weight;
        return weights;
    }
    
    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
    
}
