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

package org.opendatakit.briefcase.push;

import org.opendatakit.briefcase.model.FormStatus;

public class PushEvent {

  public static class Failure extends PushEvent {
    public Failure() {
    }
  }

  public static class Success extends PushEvent {

    private final FormStatus form;

    public Success(FormStatus form) {
      this.form = form;
    }
  }

  public static class Complete extends PushEvent {

  }

  public static class Cancel extends PushEvent {
    public final String cause;

    public Cancel(String cause) {
      this.cause = cause;
    }
  }
}
