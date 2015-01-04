#!/bin/bash

DIR=`dirname $0`

java -cp $DIR:$DIR/build:$DIR/clausie_lib/stanford-parser.jar:$DIR/clausie_lib/stanford-parser-2.0.4-models.jar:$DIR/clausie_lib/jopt-simple-4.4.jar de.mpii.clausie.ClausIE $*
