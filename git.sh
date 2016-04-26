#!/bin/bash
cd ~/software/git/MyTextClassify
git add .
if [ ! -n "$1" ] ;then
	echo "提交原因未填写！"
else
	git commit -m '"'$1'"'
	git push -u origin master
fi