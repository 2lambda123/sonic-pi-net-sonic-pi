CONFIG       += release

greaterThan(QT_MAJOR_VERSION, 4) {
    QT += widgets
}

HEADERS       = mainwindow.h \
                sonicpilexer.h \
                ruby_help.h \
                api_list.h \
                workspace.h

SOURCES       = main.cpp \
                workspace.cpp \
                mainwindow.cpp \
                sonicpilexer.cpp

RESOURCES     = application.qrc \
                help_files.qrc

LIBS         += -lqscintilla2

ICON = images/app.icns
