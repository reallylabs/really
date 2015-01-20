General Workflow
================

This is the process for committing code into master. There are of course exceptions to these rules, for example minor changes to comments and documentation, fixing a broken build

1. Make sure you have signed the really CLA, if not, sign it online.

2. Before starting to work on a feature or a fix, make sure that:

	* There is a ticket for your work in the project's issue tracker. If not, create it first.
	* The ticket has been scheduled for the current milestone.
	* The ticket is estimated by the team.
	* The ticket have been discussed and prioritized by the team.

3. You should always perform your work in a Git feature branch. The branch should be given a descriptive name that explains its intent.
 
4. When the feature or fix is completed you should open a Pull Request on GitHub.
 
5. The Pull Request should be reviewed by other maintainers (as many as feasible/practical). Note that the maintainers can consist of outside contributors, both within and outside really. Outside contributors (for example from EPFL or independent committers) are encouraged to participate in the review process, it is not a closed process.

6. After the review you should fix the issues as needed (pushing a new commit for new review etc.), iterating until the reviewers give their thumbs up. When the branch conflicts with its merge target (either by way of git merge conflict or failing CI tests), do not merge the target branch into your feature branch. Instead rebase your branch onto the target branch. Merges complicate the git history, especially for the squashing which is necessary later (see below).

7. Once the code has passed review the Pull Request can be merged into the master branch. For this purpose the commits which were added on the feature branch should be squashed into a single commit. This can be done using the command git rebase -i master (or the appropriate target branch), picking the first commit and squashing all following ones. Also make sure that the commit message conforms to the syntax specified below.

8. If the code change needs to be applied to other branches as well, create pull requests against those branches which contain the change after rebasing it onto the respective branch and await successful verification by the continuous integration infrastructure; then merge those pull requests. Please mark these pull requests with (for validation) in the title to make the purpose clear in the pull request list.

9. Once everything is said and done, associate the ticket with the “earliest” release milestone (i.e. if back-ported so that it will be in release x.y.z, find the relevant milestone for that release) and close it. You should also add yourself to the CONTRIBUTORS.md file in the root of the project.