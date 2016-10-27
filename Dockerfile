FROM java:8
COPY target/payment-to-order-processor.jar /payment-to-order-processor.jar
EXPOSE 8080
CMD ["java", "-Xms16m", "-Xmx32m", "-jar", "/payment-to-order-processor.jar"]