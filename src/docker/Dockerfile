FROM java:8
ADD ${project.build.finalName}.jar /application.jar
ADD start.sh /start.sh
EXPOSE 8080
ENTRYPOINT ["bin/bash", "/start.sh"]