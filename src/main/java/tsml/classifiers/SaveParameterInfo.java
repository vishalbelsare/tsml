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
 
/**
 * DEPRECIATED: DO NOT USE THIS INTERFACE
 * It is redundant. 
 */
package tsml.classifiers;
/**
 *
 * @author ajb
* Interface used for checkpointing a classifier. The getParameters is used in
* the ClassifierExperiments class.
* TO BE REMOVED: 
* This could be overlapping with another interface and 
* could possibly be depreciated. 
*
*/
public interface SaveParameterInfo {
    String getParameters();
    
}
