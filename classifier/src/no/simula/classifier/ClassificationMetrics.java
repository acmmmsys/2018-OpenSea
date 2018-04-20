/***********************************************************************
 * Copyright 2015 Zeno Albisser, Michael Riegler
 *
 * This file is part of OpenSea.
 *
 * OpenSea is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenSea is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenSea.  If not, see <http://www.gnu.org/licenses/>.*
 ***********************************************************************/

package no.simula.classifier;


import java.util.HashMap;

public class ClassificationMetrics extends HashMap<String, ClassificationRate> {
    public ClassificationRate get(String featureName) {
        if (!keySet().contains(featureName)) {
            put(featureName, new ClassificationRate());
        }
        return super.get(featureName);
    }

    public void print()
    {
        System.out.println("\n- - - - - - - - - - - - - - - - - - - - - - - -");
        System.out.println(Utils.padRight("Feature", 20) + ClassificationRate.getHeaders());
        for (Entry<String, ClassificationRate> entry : entrySet()) {
            String featureName = Utils.padRight(entry.getKey(), 20);
            System.out.println(featureName + entry.getValue().toString());
        }
    }
}
