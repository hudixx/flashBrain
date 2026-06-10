nohup python main.py > ocr.log 2>&1 &
ps -ef | grep main.py 
tail -f ocr.log  
 kill $(lsof -t -i:8093)