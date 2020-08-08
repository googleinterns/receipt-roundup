# Receipt Roundup

## Overview
* [Description](#description)
* [Motivation](#motivation)
* [Demos](#demos)
  * [End-to-End](#end-to-end)
  * [Logging In](#logging-in)
  * [Home Page](#home-page)
  * [Adding a Receipt](#adding-a-receipt)
  * [Receipt Analysis](#receipt-analysis)
  * [Editing a Receipt](#editing-a-receipt)
  * [Viewing Spending Analytics](#viewing-spending-analytics)
* [Technologies](#technologies)
* [Receipt Analysis and Parsing](#receipt-analysis-and-parsing)
* [Search Algorithm](#search-algorithm)
* [Commands](#commands)
* [Coding Style Guide](#coding-style-guide)
* [License](#license)
* [Contributors](#contributors)
* [Disclaimer](#disclaimer)

## Description
Receipt Roundup is a mobile web app that helps you organize your receipts. By combining computer vision with natural language processing, the web app is intelligent enough to identify information like content categories, store name, total price, and transaction date from a receipt image. To upload an image, simply log in with Google and snap a picture of your receipt. All your receipts will be made searchable by key information. Additionally, the spending analytics feature shows you a chart of your total spending per store and category.

## Motivation
Receipts are easy to lose track of and get destroyed easily. Additionally, it can be difficult to search through many receipts at once. Receipt Roundup provides an efficient way to digitally store and search receipts in minutes.


## Demos
### Logging In
![Logging In](/demos/login.gif)

### Viewing Spending Analytics
![Viewing Spending Analytics](/demos/analytics.gif)


## Technologies
* __Front-end:__ JavaScript, Bootstrap, jQuery, HTML, CSS (Follows [Material Design](https://material.io/design))

* __Back-end:__ Java servlets

* __Deployment:__ Google App Engine (GAE)

* __Storage:__ GAE Datastore NoSQL, GAE Blobstore API

* __Libraries:__ Google Cloud Vision API, Google Cloud Natural Language API, Google Charts API

* __Testing:__ JUnit4, Mockito, PowerMock, [App Engine testing utilities](https://cloud.google.com/appengine/docs/standard/java/tools/localunittesting)

* __Build Automation:__ Maven

## Receipt Analysis and Parsing
The raw text of an uploaded receipt image is extracted using the Google Cloud Vision API. This raw text is fed into the Google Cloud Natural Language API to generate relevant categories for the receipt, which are then parsed into a more human-readable and searchable format. The transaction date and total transaction price are found by splitting the raw text on whitespace, then filtering the resulting tokens for those formatted as dates or prices. The first valid date found is kept as the transaction date, and the largest price found is kept as the total price.

The Google Cloud Vision API’s Logo Detection feature is used to determine the name of the store that the receipt is from. If a logo is identified with a confidence score above 60%, the store name will be added to the receipt.

After an image is processed, the user is redirected to a receipt analysis page displaying the information extracted from the receipt image. All the fields can be edited, and any information that wasn’t extracted must be filled in by the user before saving the receipt.

## Search Algorithm
A search query returns receipts in the datastore that match user input for store name, transaction date, category, and/or price. Date range and price range are always added to the query, but store name and category are options. Right now, exact matches for store name and category are required (however, letter casing and added white space do not affect the results). 

## Commands
All commands should be executed from the root directory of the project.

### Usage and Deployment
To run the project, you need to install npm, Maven, and the Google Cloud Platform SDK.
```bash
npm install
```

Install Maven:
```bash
mvn install
```

Deploy:
```bash
gcloud config set project capstone-receipt-step-2020
mvn package:appengine deploy
```

Run a development server:
```bash
mvn package:appengine run
```

Skip tests and run a development server:
```bash
mvn package:appengine run -DskipTests
```

Clean the build by deleting the target directory:
```
mvn clean
```

### Testing
Execute all tests:
```
mvn test
```

Execute one test class file (`ClassTest.java`):
```
mvn -Dtest=ClassTest test
```

Execute one test method (`testName()` in `ClassTest.java`):
```
mvn -Dtest=ClassTest#testName test
```

### Linting
Lint files in accordance with the Google Style Guide using [Prettier](https://prettier.io/) and [Clang-Format](https://clang.llvm.org/docs/ClangFormat.html):
```
make pretty
```

Check that the HTML, CSS, and JavaScript files are valid using [ESLint](https://eslint.org/), [HTML-validate](https://html-validate.org/), and [CSS-Validator](https://github.com/w3c/css-validator):
```
make validate
```

## Coding Style Guide
Code will follow the Google style guide. Please refer to the language specific style guide at https://github.com/google/styleguide.

## License
This project is licensed under the Apache 2.0 License. See [LICENSE](LICENSE).

## Contributors
Receipt Roundup was developed by Ava ([@avagarcia0](https://github.com/avagarcia0)), Basaam ([@basaam0](https://github.com/basaam0)), and Emily ([@emilyvera](https://github.com/emilyvera)) with the support of hosts Bryan ([@bryyeh](https://github.com/bryyeh)) and Tony ([@acheng1](https://github.com/acheng1)) as a capstone project for the Google STEP Internship (Student Training in Engineering Program) in 2020.

## Disclaimer
This is not an officially supported Google product.
