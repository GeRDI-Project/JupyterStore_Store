FROM openjdk:8
COPY ./build/libs/jupyterhub-store-*.jar /usr/src/store-jhub/app.jar
WORKDIR /usr/src/store-jhub
ENTRYPOINT ["java", "-jar" , "app.jar"]