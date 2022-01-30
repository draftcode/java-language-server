#!/bin/sh
DIR=`dirname $0`
$DIR/launch_linux.sh org.javacs.Main $@ 2> >(systemd-cat -t java-language-server)
