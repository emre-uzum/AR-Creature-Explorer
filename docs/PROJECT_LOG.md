# Project Log

Please regularly update this file to record your project progress. You should be updating the project log _at least_ once a fortnight.

## Week 1 [w/c 29 September 2025]
- Received project brief and began planning the scope of the application
- Researched location-based games including Pokémon GO and Harry Potter: Wizards Unite
- Identified core technical requirements: ARCore, Google Maps API, Firebase
- Set up GitLab repository and initial Android Studio project
- Began reading documentation for ARCore and Android development

## Week 2 [w/c 6 October 2025]
- Continued background research into AR frameworks and mobile game design
- Read Azuma (1997) survey on augmented reality systems
- Researched Firebase Authentication and Firestore for cloud persistence
- Drafted initial project plan and feature list
- Set up project structure in Android Studio

## Week 3 [w/c 13 October 2025]
- Researched Room database for local data persistence
- Began planning system architecture - layered approach with presentation, game logic and platform layers
- Investigated Google Maps SDK for Android
- Read documentation on Fused Location Provider API
- Drafted initial aims and objectives

## Week 4 [w/c 20 October 2025]
- Finalised system architecture design
- Began planning data model for spawn system and player progression
- Researched deterministic spawn approaches for location-based games
- Investigated OpenStreetMap Overpass API for Points of Interest data
- Set up Firebase project and connected to Android application

## Week 5 [w/c 27 October 2025]
- Set up Firebase Authentication in the application
- Implemented basic user registration and login flow
- Connected Firestore to the application
- Began implementing player profile creation on registration
- Tested authentication flow on device

## Week 6 [w/c 3 November 2025]
- Continued refining authentication system
- Implemented password validation and username uniqueness checking
- Began designing Firestore data structure for player profiles
- Researched best practices for Firestore document organisation
- Committed initial authentication implementation

## Week 7 [w/c 10 November 2025]
- Implemented Google Maps API integration
- Set up real-time location tracking using Fused Location Provider
- Displayed user location on map with live updates
- Began implementing map marker system for creature spawns
- Tested location accuracy on device outdoors

## Week 8 [w/c 17 November 2025]
- Continued map system development
- Implemented proximity-based marker interaction
- Began researching ARCore surface detection and plane detection
- Set up ARCore dependency in build.gradle
- Read Sceneform documentation for 3D model rendering

## Week 9 [w/c 24 November 2025]
- Began ARCore integration on arcore-setup branch
- Implemented basic plane detection and surface anchoring
- Encountered initial tracking stability issues in outdoor environments
- Researched solutions to ARCore lighting and tracking problems
- Committed initial ARCore setup

## Week 10 [w/c 1 December 2025]
- Continued AR session development on ar-session branch
- Implemented AnchorNode for creature model placement
- Began loading 3D creature models using Sceneform ModelRenderable
- Encountered creature model lighting issue - models appearing darker than intended
- Began investigating lighting node configuration in Sceneform

## Week 11 [w/c 8 December 2025]
- Resolved creature model lighting issue through lighting node configuration
- Implemented tap-based capture interaction in AR mode
- Tested AR encounter on multiple surfaces and lighting conditions
- Began planning virtual encounter fallback mode
- Committed working AR session implementation

## Week 12 [w/c 15 December 2025]
- Implemented AR capture flow on ar-capture branch
- Connected AR capture outcome to Firebase - successful captures written to Firestore
- Implemented token consumption on capture attempt
- Tested capture flow end to end
- Committed AR capture implementation

## Christmas Break [w/c 22 December 2025 - 5 January 2026]
- Reviewed progress and planned remaining features
- Read additional literature on location-based game design
- Planned spawn system architecture

## Week 13 [w/c 5 January 2026]
- Began implementing capture persistence on capture-persistence branch
- Implemented Room database for local spawn data management
- Designed ActiveSpawn entity with lifecycle attributes
- Implemented SpawnRepository and ActiveSpawnDao
- Tested local database operations

## Week 14 [w/c 12 January 2026]
- Continued capture persistence implementation
- Implemented Firestore capture storage with atomic token updates
- Implemented inventory screen to display captured creatures
- Connected inventory to Firestore capture collection
- Tested persistence across sessions

## Week 15 [w/c 19 January 2026]
- Began dynamic spawn system on dynamic-spawn-system branch
- Designed grid-based spatial partitioning approach
- Implemented deterministic seeded random spawn generation
- Implemented SpawnManager with cell ID and time window seeding
- Tested spawn distribution across different geographic locations

## Week 16 [w/c 26 January 2026]
- Continued spawn system refinement
- Implemented SpawnScheduler with background ExecutorService
- Added spawn expiry and cleanup logic
- Tested spawn lifecycle - generation, expiry and replacement working correctly
- Committed spawn system implementation

## Week 17 [w/c 2 February 2026]
- Implemented map encounter flow on map-encounter-flow branch
- Connected map markers to encounter system
- Implemented encounter radius check before triggering encounter
- Added AR/virtual mode toggle with ArSupportChecker
- Tested encounter triggering on device

## Week 18 [w/c 16 February 2026]
- Implemented OpenStreetMap Overpass API integration for POIs
- Added POI markers to map interface
- Implemented POI interaction and token reward system
- Implemented POI cooldown mechanism in Firestore
- Tested POI system outdoors
- Attended interim project presentation with principal marker (19 February 2026, 2:00 PM - 2:30 PM)
- Received feedback on project progress and discussed remaining development priorities

## Week 19 [w/c 16 February 2026]
- Implemented virtual encounter mode as fallback
- Implemented VirtualEncounterActivity with 3D model rendering without ARCore
- Connected virtual encounter to same capture logic as AR mode
- Tested virtual encounter on non-ARCore device
- Committed virtual encounter implementation

## Week 20 [w/c 23 February 2026]
- Implemented map styling using custom Google Maps JSON style
- Refined UI across all screens for consistency
- Implemented bottom menu sheet on map screen
- Improved location update handling and camera management
- Committed map styling improvements

## Week 21 [w/c 2 March 2026]
- Began friends system implementation on friends-system branch
- Designed Firestore data structure for friend relationships
- Implemented friend request sending and receiving
- Implemented FriendsActivity and FriendsAdapter
- Tested friend request flow between two accounts

## Week 22 [w/c 9 March 2026]
- Continued friends system development
- Implemented recent captures display for friends
- Implemented friend profile view
- Tested social features across two test accounts
- Committed friends system implementation

## Week 23 [w/c 16 March 2026]
- Implemented account management screen on account-profile branch
- Added username display, token balance and capture count
- Implemented password change functionality
- Began implementing account deletion

## Week 24 [w/c 23 March 2026]
- Completed account deletion implementation
- Identified bug -deleted accounts not removed from friends lists
- Fixed account deletion propagation through Firestore
- Tested account deletion with two accounts - confirmed fix working
- Committed account profile and bug fix

## Week 25 [w/c 30 March 2026]
- Began final bug fixes and polish on bug-fixes-and-touches branch
- Refined UI elements across all screens
- Improved error handling and edge case management
- Conducted full end-to-end testing of gameplay loop
- Began dissertation write-up

## Week 26 [w/c 6 April 2026]
- Continued dissertation write-up
- Wrote introduction and literature review chapters
- Added citations throughout
- Continued testing and refinement of application
- Committed final bug fixes

## Week 27 [w/c 13 April 2026]
- Continued dissertation write-up
- Wrote implementation and results chapters
- Added screenshots and diagrams to dissertation
- Conducted additional testing across different environments
- Refined spawn system parameters

## Week 28 [w/c 20 April 2026]
- Finalised dissertation write-up
- Wrote critical appraisal and conclusion
- Completed abstract
- Final testing of all features
- Merged bug-fixes-and-touches branch to main
- Submitted project

