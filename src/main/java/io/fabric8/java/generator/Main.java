package io.fabric8.java.generator;

import java.io.File;

public class Main {

    public static void main(String[] args) {
        System.out.println("START");
        final Config config = new Config();
        File source = new File("apis__apps__v1_openapi.json");
        File target = new File ("tmp");
        final JavaGenerator runner = new FileJavaGenerator(config, source);
        runner.run(target);
        System.out.println("END");
    }

}
