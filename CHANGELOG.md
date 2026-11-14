# Changelog

## [0.3.0](https://github.com/i-vasu/vaabhi/compare/vaabhi-v0.2.0...vaabhi-v0.3.0) (2026-11-13)


### Features

* admin portal, money in rupees and storefront orders with Razorpay and Shiprocket ([#29](https://github.com/i-vasu/vaabhi/issues/29)) ([32567b5](https://github.com/i-vasu/vaabhi/commit/32567b5ec766c78af5a7b23583da22f0635cabd3))
* **admin:** product management — list, editor, variants, blouse options and publishing ([#21](https://github.com/i-vasu/vaabhi/issues/21)) ([da7a12b](https://github.com/i-vasu/vaabhi/commit/da7a12b70e6981b35611c20a2eb9003a64785b02))
* **admin:** stock → workshop → product flow, fabric tracing and admin polish ([#41](https://github.com/i-vasu/vaabhi/issues/41)) ([c423609](https://github.com/i-vasu/vaabhi/commit/c4236095f9114795ba99a994c698ecba55ed678a))
* **auth:** customer sign-in with Google and mobile number + OTP ([#28](https://github.com/i-vasu/vaabhi/issues/28)) ([6237914](https://github.com/i-vasu/vaabhi/commit/623791442177e9b702db65e33d45dfd4c35b3b8a))
* **bag:** bag page priced by the shop, with quantity, remove/undo and wishlist ([#25](https://github.com/i-vasu/vaabhi/issues/25)) ([0df5ef4](https://github.com/i-vasu/vaabhi/commit/0df5ef49a9b4c3faae3a7bf1b351b7027c84936f))
* **catalog:** products, variants, facets, blouse add-ons and storefront listing API ([#19](https://github.com/i-vasu/vaabhi/issues/19)) ([ea5e717](https://github.com/i-vasu/vaabhi/commit/ea5e71704c87a27cc0ec380e4e9f0ae376fb0fca))
* keep release version in sync across the monorepo and show it ([#15](https://github.com/i-vasu/vaabhi/issues/15)) ([8fe277f](https://github.com/i-vasu/vaabhi/commit/8fe277fc6b1b510a937f494c2d9adf156c9cdd9d))
* **media:** product photos on Cloudflare R2 with in-browser resizing ([#22](https://github.com/i-vasu/vaabhi/issues/22)) ([351d764](https://github.com/i-vasu/vaabhi/commit/351d76466d06182abbd7ba3abf272a7f0143be70))
* **money:** guard incoming money (phase 1), onto main ([#53](https://github.com/i-vasu/vaabhi/issues/53)) ([47e617c](https://github.com/i-vasu/vaabhi/commit/47e617c0d03123d2ad10e79e408c2148dbeef2c1))
* **storefront:** build the approved home page with the Ivory Neutral design system ([#18](https://github.com/i-vasu/vaabhi/issues/18)) ([18fbb17](https://github.com/i-vasu/vaabhi/commit/18fbb17e9af2d00b79872b559f8000b126878317))
* **storefront:** category listing page with filter and sort sheets ([#20](https://github.com/i-vasu/vaabhi/issues/20)) ([77d5dab](https://github.com/i-vasu/vaabhi/commit/77d5dabb9702c56ba34d5f537314d635d5207173))
* **storefront:** header menus, logo scroll-to-top and fuller footer ([#26](https://github.com/i-vasu/vaabhi/issues/26)) ([1806e21](https://github.com/i-vasu/vaabhi/commit/1806e21a891b15aa6654c58ae30fa9b6e47bf34b))
* **storefront:** Instagram reels on the home page and WhatsApp group join ([#45](https://github.com/i-vasu/vaabhi/issues/45)) ([0e769b2](https://github.com/i-vasu/vaabhi/commit/0e769b2452067597dff0c3942ac6793cae9878cc))
* **storefront:** managed content, no dead links, account page and guest order tracking ([#40](https://github.com/i-vasu/vaabhi/issues/40)) ([554c888](https://github.com/i-vasu/vaabhi/commit/554c888a5492d08f8b764d52272c0dc0dca9b93b))
* **storefront:** product page with colours, sizes, blouse options and add to bag ([#24](https://github.com/i-vasu/vaabhi/issues/24)) ([fc810cd](https://github.com/i-vasu/vaabhi/commit/fc810cd6547609beee9ad767e537b5cc965a7ee5))
* WhatsApp-only customer messaging (remove SMS) ([#44](https://github.com/i-vasu/vaabhi/issues/44)) ([00cf971](https://github.com/i-vasu/vaabhi/commit/00cf971c126ca405cc91d4b03570c2054b1f2807))


### Bug fixes

* **admin:** say why a photo upload failed; keep the phone keyboard open in sheets ([#43](https://github.com/i-vasu/vaabhi/issues/43)) ([34a11b9](https://github.com/i-vasu/vaabhi/commit/34a11b9dcd8fcee8513b16cdb4781beda56ec38a))
* **checkout:** cancel no longer calls the transactional shipment() on itself ([#55](https://github.com/i-vasu/vaabhi/issues/55)) ([1985812](https://github.com/i-vasu/vaabhi/commit/1985812eda77f94648d357414de0d7540f0e0860))
* **checkout:** cancel online orders before refunding, retry failed refunds ([#46](https://github.com/i-vasu/vaabhi/issues/46)) ([5b1df2c](https://github.com/i-vasu/vaabhi/commit/5b1df2c23f419460f894ba5547bb7bf50a360208))
* **ci:** run backend and frontend tests on every deploying main push ([#27](https://github.com/i-vasu/vaabhi/issues/27)) ([ea78271](https://github.com/i-vasu/vaabhi/commit/ea78271a8f465ee25f4ea35faee432af3b1f1390))
* **ci:** skip Sonar on main pushes that change no app code ([#57](https://github.com/i-vasu/vaabhi/issues/57)) ([96af05b](https://github.com/i-vasu/vaabhi/commit/96af05b7c91596189d7f9e9838628b1076543099))
* **deploy:** R2 keys from GitHub secrets; admin photo upload guidance ([#23](https://github.com/i-vasu/vaabhi/issues/23)) ([503bf46](https://github.com/i-vasu/vaabhi/commit/503bf46e2b19c9774374709635502b63f8835b2c))
* **deps:** remove duplicated entries from pnpm-lock.yaml ([#54](https://github.com/i-vasu/vaabhi/issues/54)) ([36559ef](https://github.com/i-vasu/vaabhi/commit/36559ef89777083386ed7fcf1fb22dfd9e747652))
* **e2e:** split bestsellers out of the home page smoke check ([#39](https://github.com/i-vasu/vaabhi/issues/39)) ([043ab13](https://github.com/i-vasu/vaabhi/commit/043ab132d54134a86e9578e9ae5a4c1c7cff7ea3))

## [0.2.0](https://github.com/i-vasu/vaabhi/compare/vaabhi-v0.1.0...vaabhi-v0.2.0) (2026-10-31)


### Features

* **backend:** accept Neon postgres:// connection URLs ([cb75851](https://github.com/i-vasu/vaabhi/commit/cb75851279298f1bb4fdb9edf8da8a2079f3941b))
* scaffold backend, storefront, admin, infra and CI/CD skeleton ([ec0e8a9](https://github.com/i-vasu/vaabhi/commit/ec0e8a9c073d7adf1109e92791203f37555617f7))


### Bug fixes

* never build the frontends with an empty API URL ([8ea3040](https://github.com/i-vasu/vaabhi/commit/8ea3040c4ad1d0130830c432258c4c54cea296d8))
* **security:** address SonarQube Cloud findings in SecurityConfig ([7498eb9](https://github.com/i-vasu/vaabhi/commit/7498eb9b7e32ce421078647f65ab956975e723d1))
