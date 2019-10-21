/*
 * Copyright (C) 2018 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.briefcase.export;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static org.javarosa.core.model.Constants.DATATYPE_NULL;
import static org.javarosa.core.model.DataType.GEOPOINT;
import static org.javarosa.core.model.DataType.GEOSHAPE;
import static org.javarosa.core.model.DataType.GEOTRACE;
import static org.javarosa.core.model.DataType.MULTIPLE_ITEMS;
import static org.javarosa.core.model.DataType.NULL;
import static org.opendatakit.briefcase.export.Model.ControlType.SELECT_MULTI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.javarosa.core.model.DataType;
import org.javarosa.core.model.ItemsetBinding;
import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.instance.TreeElement;
import org.opendatakit.briefcase.reused.BriefcaseException;

/**
 * This class represents a particular level in the model of a Form.
 * It can hold the root level model or any of its fields.
 */
class Model {
  private final TreeElement model;
  private Map<String, QuestionDef> controls;

  /**
   * Main constructor for {@link Model} that takes a {@link TreeElement} as its root.
   */
  Model(TreeElement model, Map<String, QuestionDef> controls) {
    this.model = model;
    this.controls = controls;
  }

  /**
   * Iterates over the children of this instance and returns the flatmapped result of mapping
   * each child using the given mapper function.
   *
   * @param mapper {@link Function} that takes a model and returns a {@link Stream} of type T
   * @param <T>    Type parameter of the output {@link Stream}
   * @return a {@link Stream} of type T
   */
  <T> Stream<T> flatMap(Function<Model, Stream<T>> mapper) {
    return children().stream().flatMap(mapper);
  }

  /**
   * Iterates over the children of this instance feeding each child to the given consumer
   * function.
   *
   * @param consumer {@link Consumer} that takes a child model
   */
  void forEach(Consumer<Model> consumer) {
    children().forEach(consumer);
  }

  /**
   * Returns the name of this {@link Model} instance, which is the
   * name of the XML tag that it represents on a Form's model.
   *
   * @return a {@link String} with the name of this {@link Model}
   */
  String getName() {
    return model.getName();
  }

  /**
   * Returns the Fully Qualified Name of this {@link Model} instance, which
   * is the concatenation of this instance's name and all its ancestors' names.
   *
   * @return a @{link String} with the FQN of this {@link Model}
   */
  String fqn() {
    return fqn(0);
  }

  /**
   * Returns the Fully Qualified Name of this {@link Model} instance, having
   * shifted a given number of names.
   */
  String fqn(int shift) {
    return fqn(model, shift);
  }

  /**
   * Returns the Fully Qualified Name of a given {@link TreeElement} model, having
   * shifted a given number of names.
   */
  private static String fqn(TreeElement model, int shift) {
    List<String> names = new ArrayList<>();
    TreeElement current = model;
    while (current.getParent() != null && current.getParent().getName() != null) {
      names.add(current.getName());
      current = (TreeElement) current.getParent();
    }
    Collections.reverse(names);
    return String.join("-", names.subList(shift, names.size()));
  }

  /**
   * Returns the {@link DataType} of this {@link Model} instance. This will
   * be normally used when this {@link Model} instance represents a terminal
   * field of a form's model.
   *
   * @return the {@link DataType} of this {@link Model} instance}
   */
  DataType getDataType() {
    return DataType.from(model.getDataType());
  }

  /**
   * Returns the {@link List} of {@link String} names that this {@link Model} instance can be
   * associated with, shifted a given number of names.
   *
   * @param shift an int with the number of names to shift from the FQN
   * @return a {@link List} of shifted {@link String} names of this {@link Model} instance
   */
  List<String> getNames(int shift, boolean splitSelectMultiples, boolean removeGroupNames) {
    if (getDataType() == NULL && model.isRepeatable())
      return singletonList("SET-OF-" + fqn(shift));
    if (getDataType() == NULL && !model.isRepeatable() && size() > 0)
      return children().stream().flatMap(e -> e.getNames(shift, splitSelectMultiples, removeGroupNames).stream()).collect(toList());
    String fieldName = removeGroupNames ? getName() : fqn(shift);
    if (getDataType() == GEOPOINT)
      return Arrays.asList(
          fieldName + "-Latitude",
          fieldName + "-Longitude",
          fieldName + "-Altitude",
          fieldName + "-Accuracy"
      );
    if (isChoiceList() && splitSelectMultiples)
      return concat(
          Stream.of(fieldName),
          getChoices().stream().map(choice -> fieldName + "/" + choice.getValue())
      ).collect(toList());
    return singletonList(fieldName);
  }

  /**
   * Returns the {@link List} of repeatable group {@link Model} children of this {@link Model}
   * instance.
   *
   * @return a {@link List} of repeatable group {@link Model} children of this {@link Model} instance
   */
  List<Model> getRepeatableFields() {
    return flatten()
        .filter(field -> field.model.getDataType() == DATATYPE_NULL && field.model.isRepeatable())
        .collect(toList());
  }

  /**
   * Returns whether this {@link Model} instance represents a repeatable group or not.
   *
   * @return true if this {@link Model} instance represents a repeatable group. False otherwise.
   */
  boolean isRepeatable() {
    return model.isRepeatable();
  }

  /**
   * Returns whether this {@link Model} instance has children {@link Model} instances or not.
   *
   * @return true if this {@link Model} instance has children {@link Model} instances. False otherwise.
   */
  boolean isEmpty() {
    return size() == 0;
  }

  /**
   * Returns the {@link Model} parent of this {@link Model} instance.
   *
   * @return the {@link Model} parent of this {@link Model} instance
   */
  Model getParent() {
    return new Model((TreeElement) model.getParent(), controls);
  }

  /**
   * Returns the number of ancestors of this {@link Model} instance.
   *
   * @return an integer with the number of ancestors of this {@link Model} instance
   */
  int countAncestors() {
    int count = 0;
    Model ancestor = this;
    while (ancestor.hasParent()) {
      count++;
      ancestor = ancestor.getParent();
    }
    // We remove one to account for the root node
    return count - 1;
  }

  /**
   * Returns whether this {@link Model} is the top-most element on a form's model.
   *
   * @return true if this {@link Model} is the top-most element on a form's model. False otherwise.
   */
  boolean isRoot() {
    return countAncestors() == 0;
  }

  boolean hasParent() {
    return model.getParent() != null;
  }

  private Stream<Model> flatten() {
    return children().stream()
        .flatMap(e -> e.size() == 0 ? Stream.of(e) : concat(Stream.of(e), e.flatten()));
  }

  private long size() {
    return model.getNumChildren();
  }

  List<Model> children() {
    Set<String> fqns = new HashSet<>();
    List<Model> children = new ArrayList<>(model.getNumChildren());
    for (int i = 0, max = model.getNumChildren(); i < max; i++) {
      Model child = new Model(model.getChildAt(i), controls);
      String fqn = child.fqn();
      if (!fqns.contains(fqn)) {
        children.add(child);
        fqns.add(fqn);
      }
    }
    return children;
  }

  boolean isChoiceList() {
    return Optional.ofNullable(controls.get(fqn()))
        .map(control -> getDataType() == MULTIPLE_ITEMS || ControlType.from(control.getControlType()) == SELECT_MULTI)
        .orElse(false);
  }


  List<SelectChoice> getChoices() {
    Optional<QuestionDef> maybeControl = Optional.ofNullable(controls.get(fqn()));

    if (!maybeControl.isPresent())
      return emptyList();

    if (maybeControl.map(QuestionDef::getAppearanceAttr).map(s -> s.contains("search(")).orElse(false))
      return emptyList();

    // Try to return dynamic choices first, then static choices
    // Dynamic choices can be present when using an internal
    // secondary itemset with a predicate
    return maybeControl
        .map(QuestionDef::getDynamicChoices)
        .map(ItemsetBinding::getChoices)
        .orElseGet(() -> maybeControl.map(QuestionDef::getChoices).orElse(emptyList()));
  }

  boolean isMetaAudit() {
    return model.getName().equals("audit") && model.getParent() != null && model.getParent().getName().equals("meta");
  }

  boolean hasAuditField() {
    return flatten()
        .filter(child -> child.getName().equals("audit"))
        .findFirst()
        .map(audit -> audit.hasParent() && audit.getParent().getName().equals("meta"))
        .orElse(false);
  }

  Model getChildByName(String name) {
    return flatten()
        .filter(child -> child.getName().equals(name))
        .findFirst()
        .orElseThrow(BriefcaseException::new);
  }

  public boolean isSpatial() {
    return Arrays.asList(GEOPOINT, GEOTRACE, GEOSHAPE).contains(getDataType());
  }

  List<Model> getSpatialFields() {
    return flatten().filter(Model::isSpatial).collect(toList());
  }

  // TODO This should be defined in JavaRosa, like the DataType enum
  enum ControlType {
    UNTYPED(-1),
    INPUT(1),
    SELECT_ONE(2),
    SELECT_MULTI(3),
    TEXTAREA(4),
    SECRET(5),
    RANGE(6),
    UPLOAD(7),
    SUBMIT(8),
    TRIGGER(9),
    IMAGE_CHOOSE(10),
    LABEL(11),
    AUDIO_CAPTURE(12),
    VIDEO_CAPTURE(13),
    OSM_CAPTURE(14),
    FILE_CAPTURE(15);

    int value;

    ControlType(int value) {
      this.value = value;
    }

    /**
     * Returns a {@link ControlType} from its int value
     *
     * @param intControlType the int value of the requested DataType
     * @return the related {@link ControlType} instance
     */
    static ControlType from(int intControlType) {
      for (ControlType ct : values()) {
        if (ct.value == intControlType)
          return ct;
      }
      throw new IllegalArgumentException("No ControlType with value " + intControlType);
    }
  }
}
