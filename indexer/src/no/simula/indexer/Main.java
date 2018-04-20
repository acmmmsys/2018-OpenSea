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

package no.simula.indexer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Main {
    private static void printUsage() {
        String execPath = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        File userDir = new File(System.getProperty("user.dir"));
        File execFile = new File(execPath);
        String relativePath = userDir.toURI().relativize(execFile.toURI()).getPath();
        System.out.println("usage: " + relativePath + " /directory/with/images [/directory/with/more/images ...] [-f ...]");
        System.out.println("       All the provided paths will be indexed and a separate index will be stored");
        System.out.println("       in a subdirectory inside each provided directory.");
        System.out.println("");
        System.out.println("       -f | -feature   A feature to use for classification. JCD is default.");
        System.out.println("                       Multiple features can be provided.");
        System.out.println("                       Possible features are for example: JCD, FCTH, EdgeHistogram, ...");
        System.out.println("       -s | -silent    Do not print progress messages.");
        System.out.println("");
        System.exit(-1);
    }

    public static void main(String[] args) {
        if (args.length == 0)
            printUsage();

        Boolean silent = false;
        ArrayList<String> featureNames = new ArrayList<>();
        ArrayList<String> directories = new ArrayList<>();
        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals("-s") || args[i].equals("-silent")) {
                silent = true;
            } else if (args[i].equals("-f") || args[i].equals("-feature")) {
                ++i;
                if (i == args.length) printUsage();
                featureNames.add(args[i]);
            } else {
                directories.add(args[i]);
            }
        }

        if (featureNames.isEmpty())
            featureNames.add("JCD");

        try {
            Indexer.main(directories, featureNames, silent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}