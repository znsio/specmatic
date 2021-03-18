# Contributing

Thanks for being willing to contribute!

## Project setup

1.  Fork and clone the repo
2.  Install JDK > 11
3.  Install Kotlin > 1.4.0
4.  We have a wrapped Gradle, so you will just need to run `gradlew`
5.  Run `./gradlew clean build` project root folder to install dependencies
6.  Create a branch for your PR with `git checkout -b your-branch-name`

> Tip: Keep your `master` branch pointing at the original repository and make
> pull requests from branches on your fork. To do this, run:
>
> ```
> git remote add upstream https://github.com/znsio/specmatic.git
> git fetch upstream
> git branch --set-upstream-to=upstream/master master
> ```
>
> This will add the original repository as a "remote" called "upstream," Then
> fetch the git information from that remote, then set your local `master`
> branch to use the upstream master branch whenever you run `git pull`. Then you
> can make all of your pull request branches based on this `master` branch.
> Whenever you want to update your version of `master`, do a regular `git pull`.

## Committing and Pushing changes

Please make sure to run the tests before you commit your changes by using the command

```./gradlew clean test```

Generate Fat Jar. The `specmatic.jar` should be available in `<projectRoot>/application/build/libs`

```./gradlew clean build shadowJar```

Run the `specmatic.jar` to verify any commands as part of your changes.

## Help needed

Please checkout the [the open issues][issues]

Also, please watch the repo and respond to questions/bug reports/feature
requests! Thanks!
