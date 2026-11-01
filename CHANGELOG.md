# Changelog

## [0.3.0](https://github.com/i-vasu/vaabhi/compare/vaabhi-v0.2.0...vaabhi-v0.3.0) (2026-11-01)


### Features

* **admin:** product management — list, editor, variants, blouse options and publishing ([#21](https://github.com/i-vasu/vaabhi/issues/21)) ([da7a12b](https://github.com/i-vasu/vaabhi/commit/da7a12b70e6981b35611c20a2eb9003a64785b02))
* **bag:** bag page priced by the shop, with quantity, remove/undo and wishlist ([#25](https://github.com/i-vasu/vaabhi/issues/25)) ([0df5ef4](https://github.com/i-vasu/vaabhi/commit/0df5ef49a9b4c3faae3a7bf1b351b7027c84936f))
* **catalog:** products, variants, facets, blouse add-ons and storefront listing API ([#19](https://github.com/i-vasu/vaabhi/issues/19)) ([ea5e717](https://github.com/i-vasu/vaabhi/commit/ea5e71704c87a27cc0ec380e4e9f0ae376fb0fca))
* keep release version in sync across the monorepo and show it ([#15](https://github.com/i-vasu/vaabhi/issues/15)) ([8fe277f](https://github.com/i-vasu/vaabhi/commit/8fe277fc6b1b510a937f494c2d9adf156c9cdd9d))
* **media:** product photos on Cloudflare R2 with in-browser resizing ([#22](https://github.com/i-vasu/vaabhi/issues/22)) ([351d764](https://github.com/i-vasu/vaabhi/commit/351d76466d06182abbd7ba3abf272a7f0143be70))
* **storefront:** build the approved home page with the Ivory Neutral design system ([#18](https://github.com/i-vasu/vaabhi/issues/18)) ([18fbb17](https://github.com/i-vasu/vaabhi/commit/18fbb17e9af2d00b79872b559f8000b126878317))
* **storefront:** category listing page with filter and sort sheets ([#20](https://github.com/i-vasu/vaabhi/issues/20)) ([77d5dab](https://github.com/i-vasu/vaabhi/commit/77d5dabb9702c56ba34d5f537314d635d5207173))
* **storefront:** header menus, logo scroll-to-top and fuller footer ([#26](https://github.com/i-vasu/vaabhi/issues/26)) ([1806e21](https://github.com/i-vasu/vaabhi/commit/1806e21a891b15aa6654c58ae30fa9b6e47bf34b))
* **storefront:** product page with colours, sizes, blouse options and add to bag ([#24](https://github.com/i-vasu/vaabhi/issues/24)) ([fc810cd](https://github.com/i-vasu/vaabhi/commit/fc810cd6547609beee9ad767e537b5cc965a7ee5))


### Bug fixes

* **ci:** run backend and frontend tests on every deploying main push ([#27](https://github.com/i-vasu/vaabhi/issues/27)) ([ea78271](https://github.com/i-vasu/vaabhi/commit/ea78271a8f465ee25f4ea35faee432af3b1f1390))
* **deploy:** R2 keys from GitHub secrets; admin photo upload guidance ([#23](https://github.com/i-vasu/vaabhi/issues/23)) ([503bf46](https://github.com/i-vasu/vaabhi/commit/503bf46e2b19c9774374709635502b63f8835b2c))

## [0.2.0](https://github.com/i-vasu/vaabhi/compare/vaabhi-v0.1.0...vaabhi-v0.2.0) (2026-10-31)


### Features

* **backend:** accept Neon postgres:// connection URLs ([cb75851](https://github.com/i-vasu/vaabhi/commit/cb75851279298f1bb4fdb9edf8da8a2079f3941b))
* scaffold backend, storefront, admin, infra and CI/CD skeleton ([ec0e8a9](https://github.com/i-vasu/vaabhi/commit/ec0e8a9c073d7adf1109e92791203f37555617f7))


### Bug fixes

* never build the frontends with an empty API URL ([8ea3040](https://github.com/i-vasu/vaabhi/commit/8ea3040c4ad1d0130830c432258c4c54cea296d8))
* **security:** address SonarQube Cloud findings in SecurityConfig ([7498eb9](https://github.com/i-vasu/vaabhi/commit/7498eb9b7e32ce421078647f65ab956975e723d1))
