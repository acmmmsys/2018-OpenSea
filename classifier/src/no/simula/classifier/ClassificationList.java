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


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class ClassificationList extends LinkedList<Classification> {
    synchronized public void insert(Classification classification) {
        this.add(classification);
    }

    void print() {
        for (Classification classification : this) {
            classification.print();
        }
    }

    void createMetrics()
    {
        ClassificationMetrics metrics = new ClassificationMetrics();
        for (Classification classification : this) {
            classification.evaluate(metrics);
        }

        metrics.print();
    }

    public static class ClassificationComparator implements Comparator<Classification> {
        public int compare(Classification x, Classification y)
        {
            int frameX = Integer.valueOf(x.getImagePath());
            int frameY = Integer.valueOf(y.getImagePath());
            int c = frameX - frameY;
            if (c < 0) return -1;
            if (c > 0) return 1;
            return 0;
        }
    }

    public void exportJSON(String inputVideo) throws IOException {
        String fileName = "results-" + System.currentTimeMillis() / 1000 + ".json";
        System.out.println("\nwriting json output to: " + fileName);

        sort(new ClassificationComparator());

        ArrayList<Integer> positiveFrames = new ArrayList<Integer>();
        ArrayList<Integer> negativeFrames = new ArrayList<Integer>();
        for (Classification classification : this) {
            SampleType typeOfFrame = (SampleType)classification.get("LateFusion");
            int frameNumber = Integer.valueOf(classification.getImagePath());
            if (typeOfFrame == SampleType.POSITIVE)
                positiveFrames.add(frameNumber);
            else
                negativeFrames.add(frameNumber);
        }

        BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
        bw.write("{"); bw.newLine();
        bw.write("    \"negativeFrames\": ["); bw.newLine();
        Iterator it = negativeFrames.iterator();
        while (it.hasNext()) {
            bw.write("        " + String.valueOf(it.next()));
            if (it.hasNext()) bw.write(",");
            bw.newLine();
        }
        bw.write("    ],"); bw.newLine();
        bw.write("    \"positiveFrames\": ["); bw.newLine();
        Iterator itp = positiveFrames.iterator();
        while (itp.hasNext()) {
            bw.write("        " + String.valueOf(itp.next()));
            if (itp.hasNext()) bw.write(",");
            bw.newLine();
        }
        bw.write("    ],"); bw.newLine();
        bw.write("\"videoName\": \"" + new File(inputVideo).getAbsolutePath() + "\""); bw.newLine();
        bw.write("}");
        bw.close();
    }

    public void createHTML() throws IOException {
        String fileName = "results-" + System.currentTimeMillis() / 1000 + ".html";
        System.out.println("\nwriting html output to: " + fileName);
        BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
        bw.write("<html>\n" +
                "<head><title>Classification Results</title></head>\n" +
                "<body bgcolor=\"#FFFFFF\">\n");
        bw.write("<table>");

        int numImages = size();
        int currentImageIndex = 0;
        for (Classification classification : this) {
            ++currentImageIndex;
            SampleType type = classification.get("LateFusion");
            String imagePath = classification.getImagePath();

            if (currentImageIndex % 3 == 0) bw.write("<tr>");
            String colorF = (type == SampleType.POSITIVE) ? "rgb(255, 0, 0)" : "rgb(0, 255, 0)";
            bw.write("<td><a href=\""
                        + imagePath
                        + "\"><img style=\"max-width:220px;border:medium solid "
                        + colorF
                        + ";\"src=\""
                        + imagePath
                        + "\" border=\"" + 5 + "\" style=\"border: 3px\n" + "black solid;\"></a></td>\n");
            if (currentImageIndex % 3 == 2) bw.write("</tr>");
        }

        if (numImages % 3 != 0) {
            if (numImages % 3 == 2) {
                bw.write("<td>-</td with exit code 0\nd>\n");
                bw.write("<td>-</td>\n");
            } else if (numImages % 3 == 2) {
                bw.write("<td>-</td>\n");
            }
            bw.write("</tr>");
        }

        bw.write("</table></body>\n" +
                "</html>");
        bw.close();
    }
}

