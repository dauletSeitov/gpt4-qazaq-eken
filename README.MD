to build docker image:
    docker build -t gpt-qazaq-eken-image .
    OR
    docker build --platform linux/arm64 -t gpt-qazaq-eken-image .

to save image as archive
    docker save -o gpt-telegram-bot.tar gpt-qazaq-eken-image:latest

to copy archive to server
    scp -i ssh-key.key gpt-telegram-bot.tar user@host:/home/ubuntu/gpt-telegram-bot

connect to server
    ssh -i /ssh-key.key user@host

to load archive 
    docker load -i gpt-telegram-bot.tar

to copy file to server
    scp -i ssh-key.key docker-compose.yml user@host:/home/ubuntu/gpt-telegram-bot

to run
    docker-compose up -d 