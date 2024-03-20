package com.flipkart.krystal.vajram.codegen;

import static com.flipkart.krystal.vajram.codegen.Constants.COGENGEN_PHASE_KEY;
import static com.flipkart.krystal.vajram.codegen.Utils.getVajramImplClassName;
import static com.flipkart.krystal.vajram.codegen.models.CodegenPhase.IMPLS;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;

import com.flipkart.krystal.vajram.codegen.models.CodegenPhase;
import com.flipkart.krystal.vajram.codegen.models.VajramInfo;
import com.google.auto.service.AutoService;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

@SupportedAnnotationTypes("com.flipkart.krystal.vajram.VajramDef")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
@SupportedOptions(COGENGEN_PHASE_KEY)
public class VajramImplGenProcessor extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Utils util = new Utils(processingEnv, this.getClass());
    String phaseString = processingEnv.getOptions().get(COGENGEN_PHASE_KEY);
    try {
      if (phaseString == null || !IMPLS.equals(CodegenPhase.valueOf(phaseString))) {
        util.note(
            "Skipping VajramImplGenProcessor since codegen phase is %s"
                .formatted(String.valueOf(phaseString)));
        return false;
      }
    } catch (IllegalArgumentException e) {
      util.error(
          ("VajramImplGenProcessor could not parse phase string '%s'. "
                  + "Exactly one of %s must be passed as value to java compiler "
                  + "via the annotation processor argument '-A%s='")
              .formatted(
                  String.valueOf(phaseString),
                  Arrays.toString(CodegenPhase.values()),
                  COGENGEN_PHASE_KEY),
          null);
    }
    List<TypeElement> vajramDefinitions = util.getVajramClasses(roundEnv);
    util.note(
        "Vajram Defs received by VajramImplGenProcessor: %s"
            .formatted(
                vajramDefinitions.stream()
                    .map(Objects::toString)
                    .collect(
                        joining(lineSeparator(), '[' + lineSeparator(), lineSeparator() + ']'))));
    for (TypeElement vajramClass : vajramDefinitions) {
      VajramInfo vajramInfo = util.computeVajramInfo(vajramClass);
      VajramCodeGenerator vajramCodeGenerator = util.createCodeGenerator(vajramInfo);

      String className =
          vajramCodeGenerator.getPackageName()
              + '.'
              + getVajramImplClassName(vajramInfo.vajramId().vajramId());
      try {
        util.generateSourceFile(className, vajramCodeGenerator.codeGenVajramImpl(), vajramClass);
      } catch (Exception e) {
        StringWriter exception = new StringWriter();
        e.printStackTrace(new PrintWriter(exception));
        util.error(
            "Error while generating file for class %s. Exception: %s"
                .formatted(className, exception),
            vajramClass);
      }
    }
    return true;
  }
}
