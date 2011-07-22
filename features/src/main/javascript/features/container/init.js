/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */


/**
 * @fileoverview Initial configuration/boot-strapping work for common container
 * to operate. This includes setting up gadgets config and global environment
 * variables.
 */
(function() {

  function initializeConfig() {
    gadgets.config.init({
      'rpc': {
        parentRelayUrl: ''
      },
      'core.io': {
        jsonProxyUrl: 'http://%host%/gadgets/makeRequest',
        proxyUrl: 'http://%host%/gadgets/proxy' +
            '?refresh=%refresh%' +
            '&container=%container%%rewriteMime%' +
            '&gadget=%gadget%/%rawurl%'
      }
    });
  }

  function initializeGlobalVars() {
    window.__API_URI = getLastScriptUri();
    window.__CONTAINER = window.__API_URI
        ? window.__API_URI.getQP('container')
        : 'default';
    window.__CONTAINER_URI = shindig.uri(document.location.href);
  }

  function getLastScriptUri() {
    var scriptEls = document.getElementsByTagName('script');
    var uri = null;
    if (scriptEls.length > 0) {
      uri = shindig.uri(scriptEls[scriptEls.length - 1].src);
      // In case script URI is relative, resolve it against window.location
      uri.resolve(shindig.uri(window.location));
    }
    return uri;
  }

  initializeConfig();
  initializeGlobalVars();
})();
