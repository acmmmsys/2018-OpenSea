# 2018-OpenSea
A repository containing the source code for the paper "OpenSea - Open Search Based Classification Tool"

OpenSea contains software for experimenting with image recognition based on global image features.

This video https://www.youtube.com/watch?v=gb2BqMuZ2h0 is a demonstration of how the software works and how it can be used. 

### Installation ###

We have tested our software on Linux, Mac OS X and Windows. For simplicity we only provide installation in- structions for Ubuntu Linux. The following installation and build instructions were tested with Ubuntu 14.04 and 16.04:

* Download Lire from http://www.lire-project.net/.
* Unzip Lire to a directory of your choice. We will refer to this location as Lire directory.
* Make sure your Lire directory contains the file lire.jar.
* Install OpenCV-Java, Apache ant and Git:
```
sudo apt-get install libopencv2.4-java ant git
```
* Download and install the Java SE Development Kit 8u45 from http://www.oracle.com.
* Make sure to have the directory containing the java compiler in your PATH environment variable.
* Clone the OpenSea repository:
```
git clone https://github.com/acmmmsys/2018-OpenSea.git
```
* Build OpenSea using ant, passing your Lire directory and your OpenCV-Java directory as command line arguments. The OpenCV-Java directory is where your java bindings for OpenCV were installed. It must contain the file opencv-248.jar, or any later version.
```
ant -Dlire=/home/me/Lire-0.9.5 -Dopencv=/usr/share/OpenCV/java dist
```
Once building finished, you should find the two files classifier.jar and indexer.jar in the subdirectory dist.
* To make sure the OpenCV-Java native libraries are found at runtime, it is further necessary to add the path to libopencv_java248.so to LD_LIBRARY_PATH. export LD_LIBRARY_PATH=/usr/lib/jni


### Usage ###

```
#!bash
cd opensea/dist
# index images (create a classifier index or index of images to be classified)
java -jar indexer.jar /my/path/with/images/to/index
# classify images in /to/be/classified, based on the images already classified in /classifier
java -jar classifier.jar -i /to/be/classified/index -c /classifier/index -f feature
```
```
usage: dist/indexer.jar /directory/with/images [/directory/with/more/images ...] [-f ...]
       All the provided paths will be indexed and a separate index will be stored
       in a subdirectory inside each provided directory.

       -f | -feature   A feature to use for classification. JCD is default.
                       Multiple features can be provided.
                       Possible features are for example: JCD, FCTH, EdgeHistogram, ...
```
```
usage: dist/classifier.jar -i /to/be/classified/index -c /classifier/index -f feature
       -c | -classifierIndex      Previously indexed training data.
                                  It is possible to provide indices of multiple training datasets.
       -p | -posClassifierIndex   Previously indexed training data, containing positives only.
                                  It is possible to provide indices of multiple training datasets.
       -n | -negClassifierIndex   Previously indexed training data, containing negatives only.
                                  It is possible to provide indices of multiple training datasets.
       -i | -input                Previously indexed data to be classified.
                                  Any indexed file starting with 'p' is considered a positive sample.
                                  Any indexed file starting with 'n' is considered a negative sample.
       -P | -inputPositive        Previously indexed data to be classified.
                                  The generated metrics rely on this index only containing positive samples.
       -N | -inputNegative        Previously indexed data to be classified.
                                  The generated metrics rely on this index only containing negative samples.
       -v | -inputVideo           Video file to classify frame by frame.
       -f | -feature              A feature to use for classification.
                                  Multiple features can be provided.
                                  Possible features are for example: JCD, FCTH, EdgeHistogram, ...
                                  The respective feature must be present in any index provided.
       -m | -measure              The measure to use. (any of: classCount, weightedByRank,
                                  weightedByDistance, weightedByAverageDistance)

       All command line options must always be used in pairs of option and value.
```