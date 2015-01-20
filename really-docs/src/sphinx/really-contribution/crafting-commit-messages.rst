The art of crafting commit messages
===================================

Usually when someone reviews your code he has no idea why you did that, Commit messages:

	* Communicate your ideas to one who is reviewing your code.
	* Set the expectation what really you are trying to make with your change.
	* Reminder for the future you "fix tests" really don't tell you much.
	* Allow generating CHANGELOG.md provide better information when browsing the history.

Commit Message Format
---------------------

Each commit message consists of a header and a body. The header has a special format that includes a **task number**, **type**, a **scope** and a **subject**:

::

	<task number><type>(<scope>): <subject>
	<BLANK LINE>
	<body>

Any line of the commit message cannot be longer 100 characters! This allows the message to be easier to read on github as well as in various git tools.

Message Header
^^^^^^^^^^^^^^

Task number
"""""""""""

	For example: RIO-42
 
Type
""""

	* feat (feature)
	* fix (bug fix)
	* docs (documentation)
	* style (formatting, missing semi colons, …)
	* refactor
	* test (when adding missing tests)

Scope
"""""

	Scope could be anything specifying place of the commit change(component name). For example really-io, really-core.

subject
"""""""

	* Subject line contains succinct description of the change.
	* use imperative, present tense: “change” not “changed” nor “changes”
	* don't capitalize first letter
	* no dot (.) at the end


Message body
^^^^^^^^^^^^

	* just as in <subject> use imperative, present tense: “change” not “changed” nor “changes”
	* includes motivation for the change and contrasts with previous behavior

Example
^^^^^^^

::

	RIO-42 feat(really-core): collectionActor implementation
 
 
	- implement CollectionActor that handle 'create', 'update' and 'delete' command
	- implement 'create' and 'update' command
	- implement PersistentModelStore
	- back to Cassandra as a journal plugin instead of Kafka and removed from the conf files