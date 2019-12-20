FROM azul/zulu-openjdk:11

EXPOSE 80

# Add the service itself
ARG JAR_FILE
ADD target/${JAR_FILE} /root/service.jar

ENTRYPOINT ["java", "-jar", "/root/service.jar"]
