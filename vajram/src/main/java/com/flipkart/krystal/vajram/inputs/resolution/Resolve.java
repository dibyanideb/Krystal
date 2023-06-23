package com.flipkart.krystal.vajram.inputs.resolution;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Resolve {
  String depName();

  String[] depInputs() default {};
}