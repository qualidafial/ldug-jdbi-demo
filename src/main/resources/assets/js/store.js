/*jshint eqeqeq:false */
(function (window) {
  'use strict';

  var DONE = 4;

  /**
   * Creates a new client side storage object and will create an empty
   * collection if no collection already exists.
   *
   * @param {string} name The name of our DB we want to use
   * @param {function} callback Our fake DB uses callbacks because in
   * real life you probably would be making AJAX calls
   */
  function Store(name, callback) {
    this._dbName = name;

    this.findAll(callback);
  }

  /**
   * Finds items based on a query given as a JS object
   *
   * @param {object} query The query to match against (i.e. {foo: 'bar'})
   * @param {function} callback	 The callback to fire when the query has
   * completed running
   *
   * @example
   * db.find({foo: 'bar', hello: 'world'}, function (data) {
	 *	 // data will return any items that have foo: bar and
	 *	 // hello: world in their properties
	 * });
   */
  Store.prototype.find = function (query, callback) {
    if (!callback) {
      return;
    }

    this.findAll(function(todos) {
      callback.call(this, todos.filter(function (todo) {
        for (var q in query) {
          if (query[q] !== todo[q]) {
            return false;
          }
        }
        return true;
      }));
    });
  };

  /**
   * Will retrieve all data from the collection
   *
   * @param {function} callback The callback to fire upon retrieving data
   */
  Store.prototype.findAll = function (callback) {
    callback = callback || function () {};

    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/api/todo');
    xhr.send();
    xhr.onreadystatechange = function() {
      if (xhr.readyState === DONE && xhr.status === 200) {
        callback.call(this, JSON.parse(xhr.responseText));
      }
    };
  };

  /**
   * Will save the given data to the DB. If no item exists it will create a new
   * item, otherwise it'll simply update an existing item's properties
   *
   * @param {object} updateData The data to save back into the DB
   * @param {function} callback The callback to fire after saving
   * @param {number} id An optional param to enter an ID of an item to update
   */
  Store.prototype.save = function (updateData, callback, id) {
    var self = this;

    var xhr = new XMLHttpRequest();

    // If an ID was actually given, find the item and update each property
    if (id) {
      xhr.open('PATCH', '/api/todo/' + id);
    } else {
      xhr.open('POST', '/api/todo');
    }
    xhr.setRequestHeader("Content-Type", "application/json");
    console.log('Sending payload', updateData);

    xhr.send(JSON.stringify(updateData));
    xhr.onreadystatechange = function() {
      if (xhr.readyState === DONE && xhr.status === 200) {
        self.findAll(callback);
      }
    };
  };

  /**
   * Will remove an item from the Store based on its ID
   *
   * @param {number} id The ID of the item you want to remove
   * @param {function} callback The callback to fire after saving
   */
  Store.prototype.remove = function (id, callback) {
    var xhr = new XMLHttpRequest();
    xhr.open('DELETE', '/api/todo/' + id);
    xhr.send();
    xhr.onreadystatechange = function() {
      if (xhr.readyState === DONE && xhr.status === 204) {
        self.findAll(callback);
      }
    };
  };

  /**
   * Will drop all storage and start fresh
   *
   * @param {function} callback The callback to fire after dropping the data
   */
  Store.prototype.drop = function (callback) {
    var xhr = new XMLHttpRequest();
    xhr.open('DELETE', '/api/todo');
    xhr.send();
    xhr.onreadystatechange = function() {
      if (xhr.readyState === DONE && xhr.status === OK) {
        self.findAll(callback);
      }
    };
  };

  // Export to window
  window.app = window.app || {};
  window.app.Store = Store;
})(window);