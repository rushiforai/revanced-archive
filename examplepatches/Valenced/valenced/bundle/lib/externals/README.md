# @valenced/externals

This package provides common external libraries bundled in React Native apps, but this does not guarantee that they will be available.

This is done to ensure that Valenced's core APIs won't change if the external libraries are removed from the app.

## Libraries

For obvious reasons, this package only maintains lookups for specific versions of libraries that Discord uses.
However, it can be adapted to work with any other React Native apps.

- `@react-navigation/native`
- `@react-navigation/stack`

- `@shopify/flash-list`

- `util` (`browserify/node-util`)
  