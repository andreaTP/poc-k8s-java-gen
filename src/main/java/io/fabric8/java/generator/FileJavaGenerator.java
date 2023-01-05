/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.java.generator;

import io.fabric8.java.generator.exceptions.JavaGeneratorException;
import io.fabric8.java.generator.nodes.AbstractJSONSchema2Pojo;
import io.fabric8.java.generator.nodes.GeneratorResult;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaProps;
import io.fabric8.kubernetes.api.model.runtime.RawExtension;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fabric8.java.generator.CRGeneratorRunner.groupToPackage;

/**
 * {@link JavaGenerator} implementation that reads CRD files or directories containing CRD files and generates
 * Java classes for them.
 */
public class FileJavaGenerator implements JavaGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileJavaGenerator.class);

  private final File source;
  private final CRGeneratorRunner crGeneratorRunner;

  public FileJavaGenerator(Config config, File source) {
    crGeneratorRunner = new CRGeneratorRunner(config);
    this.source = source;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void run(File outputDirectory) {
    if (source.isDirectory()) {
      try (Stream<Path> walk = Files.walk(source.toPath(), FileVisitOption.FOLLOW_LINKS)) {
        walk
            .map(Path::toFile)
            .filter(f -> !f.getAbsolutePath().equals(source.getAbsolutePath()))
            .forEach(f -> runOnSingleSource(f, outputDirectory));
      } catch (IOException e) {
        throw new JavaGeneratorException(
            "Error visiting the folder " + source.getAbsolutePath(), e);
      }
    } else {
      runOnSingleSource(source, outputDirectory);
    }
  }

  private void runOnSingleSource(File source, File basePath) {
    try (FileInputStream fis = new FileInputStream(source)) {
      List<Object> resources = new ArrayList<>();

      Object deserialized = Serialization.unmarshal(fis);
      if (deserialized instanceof List) {
        resources.addAll((List<Object>) deserialized);
      } else {
        resources.add(deserialized);
      }

      Config config = new Config();
      String prefix = "";
      String suffix = "";
      String pkg = "io.k8s.apimachinery.pkg.apis.meta.v1";

      List<GeneratorResult.ClassResult> classResults = new LinkedList<>();

      resources.parallelStream()
          .forEach(
              rawResource -> {
                System.out.println(rawResource.getClass());
                if (rawResource instanceof RawExtension) {
                  Object value = ((RawExtension) rawResource).getValue();
                  if (value instanceof Map) {
                    Map<String, Object> schemas = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) value).get("components")).get("schemas");

                    Object rawTime = schemas.get("io.k8s.apimachinery.pkg.apis.meta.v1.Time");
                    JSONSchemaProps time = Serialization.unmarshal(Serialization.asJson(rawTime), JSONSchemaProps.class);

                    AbstractJSONSchema2Pojo example = AbstractJSONSchema2Pojo.fromJsonSchema("time", time, pkg, prefix, suffix, config);
                    classResults.addAll(validateAndAggregate(example));
                  }
                }
              });


      new WritableCRCompilationUnit(classResults).writeAllJavaClasses(new File("tmp"), pkg);

    } catch (FileNotFoundException e) {
      throw new JavaGeneratorException("File " + source.getAbsolutePath() + " not found", e);
    } catch (IOException e) {
      throw new JavaGeneratorException("Exception reading " + source.getAbsolutePath(), e);
    }
  }

  private List<GeneratorResult.ClassResult> validateAndAggregate(
          AbstractJSONSchema2Pojo... generators) {
    return Arrays.stream(generators)
            .filter(Objects::nonNull)
            .map(AbstractJSONSchema2Pojo::generateJava)
            .flatMap(g -> g.getTopLevelClasses().stream())
            .collect(Collectors.toList());
  }
}
